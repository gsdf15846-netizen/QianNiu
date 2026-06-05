package com.qianniu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianniu.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class NovelConverterService {

    private static final String SYSTEM_PROMPT = """
            你是一位专业的剧本改编专家，精通将中文小说改编为标准影视剧本格式。

            改编原则：
            1. 保留原著核心情节，不增减主要事件
            2. 将叙述性文字转为简洁客观的动作描述（action）
            3. 直接引用原文对白作为台词（dialogue），保持人物语气
            4. 根据地点/时间变化合理分割场景
            5. 识别所有出场人物，整理人物表，记录别名

            输出格式：严格的 JSON（不加任何额外文字或代码块标记），结构如下：
            {
              "metadata": {
                "title": "剧本标题",
                "source_novel": "原著名称或章节名",
                "author": "AI辅助生成",
                "created_at": "ISO8601时间",
                "total_chapters": 章节数整数
              },
              "characters": [
                {"id": "char_001", "name": "姓名", "aliases": ["别名1","别名2"], "description": "人物简介"}
              ],
              "chapters": [
                {
                  "chapter_number": 1,
                  "title": "章节标题",
                  "scenes": [
                    {
                      "scene_number": 1,
                      "heading": {
                        "location_type": "INT",
                        "place": "具体地点",
                        "time": "DAY"
                      },
                      "synopsis": "本场景一句话概要",
                      "elements": [
                        {"type": "action", "content": "动作描述（客观第三人称）"},
                        {"type": "dialogue", "character": "角色名", "parenthetical": "可选表演指导", "content": "台词"},
                        {"type": "transition", "content": "切入——"}
                      ]
                    }
                  ]
                }
              ]
            }

            字段约束（必须严格遵守）：
            - location_type 只能是 INT / EXT / INT/EXT
            - time 只能是 DAY / NIGHT / DUSK / DAWN / CONTINUOUS / LATER / MOMENTS LATER
            - type 只能是 action / dialogue / transition / note
            - dialogue 的 parenthetical 字段可省略不写
            - aliases 可为空列表 []
            - synopsis 每个场景必填，一句话概括
            - chapter_number 和 scene_number 必须是整数
            - 只输出合法 JSON""";

    private final QwenService qwenService;
    private final ObjectMapper objectMapper;

    public NovelConverterService(QwenService qwenService, ObjectMapper objectMapper) {
        this.qwenService = qwenService;
        this.objectMapper = objectMapper;
    }

    public ConvertResponse convert(String novelTitle, String novelText) {
        try {
            String rawJson = qwenService.chat(SYSTEM_PROMPT, novelText);
            String cleanJson = extractJson(rawJson);

            Map<?, ?> parsed = objectMapper.readValue(cleanJson, Map.class);
            Screenplay screenplay = mapToScreenplay(parsed, novelTitle);
            String yaml = toYaml(screenplay);

            return ConvertResponse.ok(yaml, screenplay);
        } catch (Exception e) {
            log.error("Conversion failed", e);
            return ConvertResponse.error("转换失败：" + e.getMessage());
        }
    }

    private String extractJson(String raw) {
        String text = raw.trim();
        Pattern fence = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
        Matcher m = fence.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    @SuppressWarnings("unchecked")
    private Screenplay mapToScreenplay(Map<?, ?> map, String defaultTitle) {
        // --- metadata ---
        Map<String, Object> meta = (Map<String, Object>) map.getOrDefault("metadata", Map.of());
        String now = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        ScreenplayMetadata metadata = new ScreenplayMetadata(
                str(meta, "title", defaultTitle),
                str(meta, "source_novel", defaultTitle),
                str(meta, "author", "AI辅助生成"),
                str(meta, "created_at", now),
                toInt(meta.get("total_chapters"), 1)
        );

        // --- characters ---
        List<Character> characters = ((List<Map<String, Object>>) map.getOrDefault("characters", List.of()))
                .stream().map(c -> new Character(
                        str(c, "id", "char_001"),
                        str(c, "name", "未知"),
                        (List<String>) c.getOrDefault("aliases", List.of()),
                        str(c, "description", "")
                )).toList();

        // --- chapters ---
        List<Chapter> chapters = ((List<Map<String, Object>>) map.getOrDefault("chapters", List.of()))
                .stream().map(ch -> {
                    List<Scene> scenes = ((List<Map<String, Object>>) ch.getOrDefault("scenes", List.of()))
                            .stream().map(s -> {
                                Map<String, Object> h = (Map<String, Object>) s.getOrDefault("heading", Map.of());
                                SceneHeading heading = new SceneHeading(
                                        str(h, "location_type", "INT"),
                                        str(h, "place", ""),
                                        str(h, "time", "DAY")
                                );
                                List<SceneElement> elements = ((List<Map<String, Object>>) s.getOrDefault("elements", List.of()))
                                        .stream().map(e -> new SceneElement(
                                                str(e, "type", "action"),
                                                (String) e.get("character"),
                                                (String) e.get("parenthetical"),
                                                str(e, "content", "")
                                        )).toList();
                                return new Scene(
                                        toInt(s.get("scene_number"), 1),
                                        heading,
                                        str(s, "synopsis", ""),
                                        elements
                                );
                            }).toList();
                    return new Chapter(
                            toInt(ch.get("chapter_number"), 1),
                            str(ch, "title", ""),
                            scenes
                    );
                }).toList();

        return new Screenplay(metadata, characters, chapters);
    }

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

        // metadata
        ScreenplayMetadata m = sp.getMetadata();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("title", m.getTitle());
        meta.put("source_novel", m.getSourceNovel());
        meta.put("author", m.getAuthor());
        meta.put("created_at", m.getCreatedAt());
        meta.put("total_chapters", m.getTotalChapters());
        map.put("metadata", meta);

        // characters
        map.put("characters", sp.getCharacters().stream().map(c -> {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("id", c.getId());
            cm.put("name", c.getName());
            cm.put("aliases", c.getAliases() != null ? c.getAliases() : List.of());
            cm.put("description", c.getDescription());
            return cm;
        }).toList());

        // chapters
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
                }).toList());
                return sm;
            }).toList());
            return chm;
        }).toList());

        return map;
    }

    private static String str(Map<?, ?> map, String key, String def) {
        Object v = map.get(key);
        return v instanceof String s ? s : def;
    }

    private static int toInt(Object v, int def) {
        if (v instanceof Integer i) return i;
        if (v instanceof Number n) return n.intValue();
        return def;
    }
}
