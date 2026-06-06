package com.qianniu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianniu.model.*;
import com.qianniu.model.Character;
import com.qianniu.repository.ConversionHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NovelConverterService {

    private static final int CHUNK_SIZE = 3000;

    private static final String SYSTEM_PROMPT = """
            你是一位专业的剧本改编专家，精通将中文小说改编为标准影视剧本格式。

            改编原则：
            1. 保留原著核心情节，不增减主要事件
            2. 将叙述性文字转为简洁客观的动作描述（action）
            3. 直接引用原文对白作为台词（dialogue），保持人物语气
            4. 根据地点/时间变化合理分割场景
            5. 沿用已知人物表中的 id 和 name，不重复创建已有人物

            输出格式：严格的 JSON（不加任何额外文字或代码块标记），结构如下：
            {
              "scenes": [
                {
                  "scene_number": 1,
                  "heading": {"location_type": "INT", "place": "具体地点", "time": "DAY"},
                  "synopsis": "本场景一句话概要",
                  "elements": [
                    {"type": "action", "content": "动作描述"},
                    {"type": "dialogue", "character": "角色名", "parenthetical": "可选", "content": "台词"},
                    {"type": "transition", "content": "切入——"}
                  ]
                }
              ],
              "new_characters": [
                {"id": "char_001", "name": "姓名", "aliases": [], "description": "人物简介"}
              ],
              "chunk_synopsis": "本段内容一句话概括"
            }

            字段约束（必须严格遵守）：
            - location_type 只能是 INT / EXT / INT/EXT
            - time 只能是 DAY / NIGHT / DUSK / DAWN / CONTINUOUS / LATER / MOMENTS LATER
            - type 只能是 action / dialogue / transition / note
            - dialogue 的 parenthetical 字段可省略不写
            - aliases 可为空列表 []
            - synopsis 每个场景必填，一句话概括
            - scene_number 必须是整数
            - 只输出合法 JSON""";

    private final QwenService qwenService;
    private final ObjectMapper objectMapper;
    private final ConversionCacheService cacheService;
    private final ConversionHistoryRepository historyRepo;

    public NovelConverterService(QwenService qwenService, ObjectMapper objectMapper,
                                 ConversionCacheService cacheService,
                                 ConversionHistoryRepository historyRepo) {
        this.qwenService = qwenService;
        this.objectMapper = objectMapper;
        this.cacheService = cacheService;
        this.historyRepo = historyRepo;
    }

    // ─── Public API ────────────────────────────────────────────────────────────

    public ConvertResponse convert(String novelTitle, String novelText, String rawConvId) {
        try {
            List<ChapterText> chapterTexts = splitChapters(novelText);
            log.info("Detected {} chapter(s)", chapterTexts.size());

            boolean isResume = rawConvId != null && !rawConvId.isBlank();
            final String convId;
            final List<Character> allCharacters;
            final List<String> chapterSynopses;

            if (isResume) {
                convId = rawConvId.trim();
                if (!cacheService.exists(convId)) {
                    // 缓存过期，尝试从 MySQL 返回已完成结果
                    Optional<ConversionHistory> hist = historyRepo.findByConversionId(convId);
                    if (hist.isPresent() && "COMPLETED".equals(hist.get().getStatus())) {
                        log.info("Cache expired but MySQL has completed result for {}", convId);
                        return ConvertResponse.ok(hist.get().getYamlResult(), null, convId);
                    }
                    return ConvertResponse.error("续传缓存已过期（超过24小时），请重新上传全文");
                }
                ConversionCacheService.ConversionState state = cacheService.loadState(convId);
                allCharacters = state != null ? new ArrayList<>(state.characters()) : new ArrayList<>();
                chapterSynopses = state != null ? new ArrayList<>(state.synopses()) : new ArrayList<>();
                log.info("Resuming {}: {}/{} chapters in cache", convId, chapterSynopses.size(), chapterTexts.size());
            } else {
                convId = UUID.randomUUID().toString();
                allCharacters = new ArrayList<>();
                chapterSynopses = new ArrayList<>();
                ConversionHistory history = new ConversionHistory();
                history.setConversionId(convId);
                history.setTitle(novelTitle);
                history.setCreatedAt(LocalDateTime.now());
                history.setTotalChapters(chapterTexts.size());
                history.setCompletedChapters(0);
                history.setStatus("IN_PROGRESS");
                historyRepo.save(history);
                cacheService.initState(convId, novelTitle, chapterTexts.size());
            }

            List<Chapter> chapters = new ArrayList<>();

            for (ChapterText ct : chapterTexts) {
                Chapter chapter;
                if (cacheService.isChapterCached(convId, ct.number())) {
                    List<Map<String, Object>> cached = cacheService.loadChapterScenes(convId, ct.number());
                    chapter = new Chapter(ct.number(), ct.title(),
                            mapsToScenes(cached != null ? cached : Collections.emptyList()));
                    log.info("Chapter {} loaded from Redis cache ({} scenes)",
                            ct.number(), chapter.getScenes().size());
                } else {
                    String plotSoFar = buildPlotSummary(chapterTexts, chapterSynopses);
                    ChapterResult result = processChapter(ct, allCharacters, plotSoFar);
                    chapter = result.chapter;
                    chapterSynopses.add(result.synopsis);

                    cacheService.saveChapterScenes(convId, ct.number(), scenesToMaps(chapter.getScenes()));
                    cacheService.saveState(convId, allCharacters, chapterSynopses);
                    cacheService.updateProgress(convId, chapterSynopses.size());

                    historyRepo.findByConversionId(convId).ifPresent(h -> {
                        h.setCompletedChapters(chapterSynopses.size());
                        historyRepo.save(h);
                    });
                    log.info("Chapter {} processed ({} scenes)", ct.number(), chapter.getScenes().size());
                }
                chapters.add(chapter);
            }

            String now = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            ScreenplayMetadata metadata = new ScreenplayMetadata(
                    novelTitle, novelTitle, "AI辅助生成", now, chapters.size());
            Screenplay screenplay = new Screenplay(metadata, allCharacters, chapters);
            String yaml = toYaml(screenplay);

            historyRepo.findByConversionId(convId).ifPresent(h -> {
                h.setYamlResult(yaml);
                h.setCompletedChapters(chapters.size());
                h.setTotalChapters(chapters.size());
                h.setStatus("COMPLETED");
                historyRepo.save(h);
            });

            return ConvertResponse.ok(yaml, screenplay, convId);

        } catch (Exception e) {
            log.error("Conversion failed", e);
            return ConvertResponse.error("转换失败：" + e.getMessage());
        }
    }

    // ─── Internal records ──────────────────────────────────────────────────────

    private record ChapterText(int number, String title, String text) {}
    private record ChapterResult(Chapter chapter, String synopsis) {}
    private record ChunkResult(List<Scene> scenes, List<Character> newCharacters, String synopsis) {}

    // ─── Chapter splitting ─────────────────────────────────────────────────────

    private List<ChapterText> splitChapters(String text) {
        Pattern pattern = Pattern.compile(
                "(?m)^(第[一二三四五六七八九十百千零0-9]+[章节回篇][ \\t]*[^\\n]*)",
                Pattern.MULTILINE);
        Matcher m = pattern.matcher(text);

        List<int[]> positions = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        while (m.find()) {
            positions.add(new int[]{m.start(), m.end()});
            titles.add(m.group(1).trim());
        }

        if (positions.isEmpty()) {
            return List.of(new ChapterText(1, firstLine(text), text.trim()));
        }

        List<ChapterText> result = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            int bodyStart = positions.get(i)[1];
            int bodyEnd = (i + 1 < positions.size()) ? positions.get(i + 1)[0] : text.length();
            result.add(new ChapterText(i + 1, titles.get(i), text.substring(bodyStart, bodyEnd).trim()));
        }
        return result;
    }

    private String firstLine(String text) {
        for (String line : text.split("\\n")) {
            String s = line.trim();
            if (!s.isEmpty()) return s.length() > 20 ? s.substring(0, 20) : s;
        }
        return "全文";
    }

    // ─── Chunk splitting ───────────────────────────────────────────────────────

    private List<String> splitIntoChunks(String text) {
        if (text.length() <= CHUNK_SIZE) return List.of(text);
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            if (end < text.length()) {
                int lastPara = text.lastIndexOf("\n\n", end);
                if (lastPara > start + CHUNK_SIZE / 2) {
                    end = lastPara;
                } else {
                    int lastSentence = Math.max(
                            text.lastIndexOf("。", end),
                            Math.max(text.lastIndexOf("！", end), text.lastIndexOf("？", end)));
                    if (lastSentence > start + CHUNK_SIZE / 2) end = lastSentence + 1;
                }
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) chunks.add(chunk);
            start = end;
            while (start < text.length() && text.charAt(start) == '\n') start++;
        }
        return chunks;
    }

    // ─── Chapter processing ────────────────────────────────────────────────────

    private ChapterResult processChapter(ChapterText ct, List<Character> allCharacters, String plotSoFar)
            throws Exception {
        List<String> chunks = splitIntoChunks(ct.text());
        log.info("Chapter {} '{}': {} chunk(s)", ct.number(), ct.title(), chunks.size());

        List<Scene> scenes = new ArrayList<>();
        StringBuilder synopsis = new StringBuilder();
        String prevChunkSynopsis = "";

        for (int i = 0; i < chunks.size(); i++) {
            String userMsg = buildUserMessage(ct, i, chunks.size(), scenes.size() + 1,
                    allCharacters, plotSoFar, prevChunkSynopsis, chunks.get(i));
            String rawJson = qwenService.chat(SYSTEM_PROMPT, userMsg);
            ChunkResult cr = parseChunkResult(rawJson, scenes.size() + 1);

            scenes.addAll(cr.scenes());
            mergeCharacters(allCharacters, cr.newCharacters());

            prevChunkSynopsis = cr.synopsis();
            if (synopsis.length() > 0) synopsis.append("；");
            synopsis.append(cr.synopsis());
        }
        return new ChapterResult(new Chapter(ct.number(), ct.title(), scenes), synopsis.toString());
    }

    // ─── Prompt building ───────────────────────────────────────────────────────

    private String buildUserMessage(ChapterText ct, int chunkIndex, int totalChunks,
                                    int sceneNumberStart, List<Character> knownChars,
                                    String plotSoFar, String prevChunkSynopsis, String chunkText) {
        StringBuilder sb = new StringBuilder();
        if (!knownChars.isEmpty()) {
            sb.append("【已知人物表】（请沿用已有人物的 id 和 name，不要重复创建）\n");
            for (Character c : knownChars) {
                sb.append(c.getId()).append(" ").append(c.getName())
                        .append("：").append(c.getDescription()).append("\n");
            }
            sb.append("\n");
        }
        if (!plotSoFar.isBlank()) {
            sb.append("【前情提要】\n").append(plotSoFar).append("\n");
        }
        if (!prevChunkSynopsis.isBlank()) {
            sb.append("【本章前段概要】\n").append(prevChunkSynopsis).append("\n\n");
        }
        sb.append("【当前任务】\n将以下文字改编为剧本。\n");
        sb.append("章节：第").append(ct.number()).append("章《").append(ct.title()).append("》");
        if (totalChunks > 1) {
            sb.append("，第").append(chunkIndex + 1).append("段（共").append(totalChunks).append("段）");
        }
        sb.append("\nscene_number 从 ").append(sceneNumberStart).append(" 开始编号\n\n");
        sb.append("【原文】\n").append(chunkText);
        return sb.toString();
    }

    // ─── Response parsing ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private ChunkResult parseChunkResult(String rawJson, int sceneNumberStart) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(extractJson(rawJson), Map.class);
            List<Scene> scenes = parseScenes(castList(parsed.get("scenes")), sceneNumberStart);
            List<Character> newChars = parseNewCharacters(castList(parsed.get("new_characters")));
            return new ChunkResult(scenes, newChars, str(parsed, "chunk_synopsis", ""));
        } catch (Exception e) {
            log.warn("Failed to parse chunk result, skipping: {}", e.getMessage());
            return new ChunkResult(Collections.emptyList(), Collections.emptyList(), "");
        }
    }

    private List<Scene> parseScenes(List<Map<String, Object>> rawScenes, int sceneNumberStart) {
        List<Scene> scenes = new ArrayList<>();
        int sceneNum = sceneNumberStart;
        for (Map<String, Object> s : rawScenes) {
            Map<String, Object> h = castMap(s.get("heading"));
            SceneHeading heading = new SceneHeading(
                    str(h, "location_type", "INT"), str(h, "place", ""), str(h, "time", "DAY"));
            List<SceneElement> elements = new ArrayList<>();
            for (Map<String, Object> e : castList(s.get("elements"))) {
                elements.add(new SceneElement(str(e, "type", "action"),
                        (String) e.get("character"), (String) e.get("parenthetical"),
                        str(e, "content", "")));
            }
            scenes.add(new Scene(sceneNum++, heading, str(s, "synopsis", ""), elements));
        }
        return scenes;
    }

    @SuppressWarnings("unchecked")
    private List<Character> parseNewCharacters(List<Map<String, Object>> rawChars) {
        List<Character> result = new ArrayList<>();
        for (Map<String, Object> c : rawChars) {
            List<String> aliases = c.get("aliases") instanceof List
                    ? (List<String>) c.get("aliases") : Collections.emptyList();
            result.add(new Character(str(c, "id", ""), str(c, "name", ""), aliases, str(c, "description", "")));
        }
        return result;
    }

    // ─── Character merging ─────────────────────────────────────────────────────

    private void mergeCharacters(List<Character> existing, List<Character> newChars) {
        Set<String> names = existing.stream().map(c -> c.getName().toLowerCase()).collect(Collectors.toSet());
        for (Character nc : newChars) {
            if (nc.getName().isBlank() || names.contains(nc.getName().toLowerCase())) continue;
            existing.add(new Character(String.format("char_%03d", existing.size() + 1),
                    nc.getName(), nc.getAliases(), nc.getDescription()));
            names.add(nc.getName().toLowerCase());
        }
    }

    // ─── Plot summary ──────────────────────────────────────────────────────────

    private String buildPlotSummary(List<ChapterText> allChapters, List<String> synopses) {
        if (synopses.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < synopses.size(); i++) {
            sb.append("第").append(allChapters.get(i).number()).append("章《")
                    .append(allChapters.get(i).title()).append("》：")
                    .append(synopses.get(i)).append("\n");
        }
        return sb.toString();
    }

    // ─── Redis serialization helpers ───────────────────────────────────────────

    /** Scene objects → maps for Redis storage（保留 scene_number）*/
    private List<Map<String, Object>> scenesToMaps(List<Scene> scenes) {
        return scenes.stream().map(s -> {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("scene_number", s.getSceneNumber());
            SceneHeading hd = s.getHeading();
            sm.put("heading", Map.of("location_type", hd.getLocationType(),
                    "place", hd.getPlace(), "time", hd.getTime()));
            sm.put("synopsis", s.getSynopsis());
            sm.put("elements", s.getElements().stream().map(e -> {
                Map<String, Object> em = new LinkedHashMap<>();
                em.put("type", e.getType());
                if (e.getCharacter() != null) em.put("character", e.getCharacter());
                if (e.getParenthetical() != null) em.put("parenthetical", e.getParenthetical());
                em.put("content", e.getContent());
                return em;
            }).collect(Collectors.toList()));
            return sm;
        }).collect(Collectors.toList());
    }

    /** Maps from Redis → Scene objects（scene_number 直接取缓存值，不重新编号）*/
    private List<Scene> mapsToScenes(List<Map<String, Object>> sceneMaps) {
        List<Scene> scenes = new ArrayList<>();
        for (Map<String, Object> s : sceneMaps) {
            Map<String, Object> h = castMap(s.get("heading"));
            SceneHeading heading = new SceneHeading(
                    str(h, "location_type", "INT"), str(h, "place", ""), str(h, "time", "DAY"));
            List<SceneElement> elements = new ArrayList<>();
            for (Map<String, Object> e : castList(s.get("elements"))) {
                elements.add(new SceneElement(str(e, "type", "action"),
                        (String) e.get("character"), (String) e.get("parenthetical"),
                        str(e, "content", "")));
            }
            int sceneNum = s.get("scene_number") instanceof Number
                    ? ((Number) s.get("scene_number")).intValue() : scenes.size() + 1;
            scenes.add(new Scene(sceneNum, heading, str(s, "synopsis", ""), elements));
        }
        return scenes;
    }

    // ─── YAML serialization ────────────────────────────────────────────────────

    private String toYaml(Screenplay sp) {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setAllowUnicode(true);
        opts.setIndent(2);
        opts.setIndicatorIndent(2);
        opts.setIndentWithIndicator(true);
        Yaml yaml = new Yaml(opts);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("screenplay", buildScreenplayMap(sp));
        return yaml.dump(root);
    }

    private Map<String, Object> buildScreenplayMap(Screenplay sp) {
        Map<String, Object> map = new LinkedHashMap<>();
        ScreenplayMetadata m = sp.getMetadata();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("title", m.getTitle());
        meta.put("source_novel", m.getSourceNovel());
        meta.put("author", m.getAuthor());
        meta.put("created_at", m.getCreatedAt());
        meta.put("total_chapters", m.getTotalChapters());
        map.put("metadata", meta);

        map.put("characters", sp.getCharacters().stream().map(c -> {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("id", c.getId());
            cm.put("name", c.getName());
            cm.put("aliases", c.getAliases() != null ? c.getAliases() : Collections.emptyList());
            cm.put("description", c.getDescription());
            return cm;
        }).collect(Collectors.toList()));

        map.put("chapters", sp.getChapters().stream().map(ch -> {
            Map<String, Object> chm = new LinkedHashMap<>();
            chm.put("chapter_number", ch.getChapterNumber());
            chm.put("title", ch.getTitle());
            chm.put("scenes", ch.getScenes().stream().map(s -> {
                Map<String, Object> sm = new LinkedHashMap<>();
                sm.put("scene_number", s.getSceneNumber());
                SceneHeading hd = s.getHeading();
                Map<String, Object> hdm = new LinkedHashMap<>();
                hdm.put("location_type", hd.getLocationType());
                hdm.put("place", hd.getPlace());
                hdm.put("time", hd.getTime());
                sm.put("heading", hdm);
                sm.put("synopsis", s.getSynopsis());
                sm.put("elements", s.getElements().stream().map(e -> {
                    Map<String, Object> em = new LinkedHashMap<>();
                    em.put("type", e.getType());
                    if (e.getCharacter() != null) em.put("character", e.getCharacter());
                    if (e.getParenthetical() != null) em.put("parenthetical", e.getParenthetical());
                    em.put("content", e.getContent());
                    return em;
                }).collect(Collectors.toList()));
                return sm;
            }).collect(Collectors.toList()));
            return chm;
        }).collect(Collectors.toList()));

        return map;
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private String extractJson(String raw) {
        String text = raw.trim();
        Matcher m = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```").matcher(text);
        if (m.find()) return m.group(1).trim();
        int start = text.indexOf('{'), end = text.lastIndexOf('}');
        return (start >= 0 && end > start) ? text.substring(start, end + 1) : text;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object o) {
        return o instanceof List ? (List<Map<String, Object>>) o : Collections.emptyList();
    }

    private static String str(Map<?, ?> map, String key, String def) {
        Object v = map.get(key);
        return v instanceof String ? (String) v : def;
    }
}
