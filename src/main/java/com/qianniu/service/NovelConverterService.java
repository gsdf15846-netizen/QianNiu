package com.qianniu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianniu.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.time.LocalDateTime;
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
            你是一位专业的剧本改编专家，精通将中文小说改编为标准剧本格式。

            改编原则：
            1. 保留原著核心情节，不增减主要事件
            2. 将叙述性文字转为简洁客观的动作描述（ACTION）
            3. 直接引用原文对白作为台词（DIALOGUE）
            4. 根据地点/时间变化合理分割场景
            5. 为每个角色建立简短的人物卡片

            输出格式：严格的 JSON，无任何额外文字，结构如下：
            {
              "title": "章节标题",
              "characters": [
                {"id": "char_001", "name": "角色名", "description": "角色特征"}
              ],
              "scenes": [
                {
                  "scene_id": "S001",
                  "title": "场景名称",
                  "location": "内/外 地点名称",
                  "time_of_day": "DAY/NIGHT/DAWN/DUSK/CONTINUOUS",
                  "elements": [
                    {"type": "ACTION", "content": "动作描述"},
                    {"type": "DIALOGUE", "character": "角色名", "content": "台词", "direction": "表演指导（可选，如激动地）"},
                    {"type": "TRANSITION", "content": "切入/切出/淡入/淡出"}
                  ]
                }
              ]
            }

            约束：
            - type 仅限 ACTION / DIALOGUE / TRANSITION / SOUND
            - time_of_day 仅限 DAY / NIGHT / DAWN / DUSK / CONTINUOUS
            - DIALOGUE 的 direction 字段可省略
            - 只输出合法 JSON，不加 markdown 代码块""";

    private final QwenService qwenService;
    private final ObjectMapper objectMapper;

    public NovelConverterService(QwenService qwenService, ObjectMapper objectMapper) {
        this.qwenService = qwenService;
        this.objectMapper = objectMapper;
    }

    public ConvertResponse convert(String title, String novelText) {
        try {
            String rawJson = qwenService.chat(SYSTEM_PROMPT, novelText);
            String cleanJson = extractJson(rawJson);

            Map<?, ?> parsed = objectMapper.readValue(cleanJson, Map.class);
            Script script = mapToScript(parsed, title);
            String yaml = toYaml(script);

            return ConvertResponse.ok(yaml, script);
        } catch (Exception e) {
            log.error("Conversion failed", e);
            return ConvertResponse.error("转换失败：" + e.getMessage());
        }
    }

    private String extractJson(String raw) {
        String text = raw.trim();
        // Strip markdown code fences if present
        Pattern fence = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
        Matcher m = fence.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        // Find first { to last }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    @SuppressWarnings("unchecked")
    private Script mapToScript(Map<?, ?> map, String defaultTitle) {
        String title = map.containsKey("title") ? (String) map.get("title") : defaultTitle;
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        List<Character> characters = ((List<Map<String, Object>>) map.getOrDefault("characters", List.of()))
                .stream().map(c -> new Character(
                        (String) c.getOrDefault("id", "char_001"),
                        (String) c.getOrDefault("name", "未知"),
                        (String) c.getOrDefault("description", "")
                )).toList();

        List<Scene> scenes = ((List<Map<String, Object>>) map.getOrDefault("scenes", List.of()))
                .stream().map(s -> {
                    List<SceneElement> elements = ((List<Map<String, Object>>) s.getOrDefault("elements", List.of()))
                            .stream().map(e -> {
                                ElementType type = ElementType.valueOf(
                                        ((String) e.getOrDefault("type", "ACTION")).toUpperCase());
                                return new SceneElement(
                                        type,
                                        (String) e.get("character"),
                                        (String) e.getOrDefault("content", ""),
                                        (String) e.get("direction")
                                );
                            }).toList();
                    return new Scene(
                            (String) s.getOrDefault("scene_id", "S001"),
                            (String) s.getOrDefault("title", ""),
                            (String) s.getOrDefault("location", ""),
                            (String) s.getOrDefault("time_of_day", "DAY"),
                            elements
                    );
                }).toList();

        return new Script(title, defaultTitle, now, characters, scenes);
    }

    private String toYaml(Script script) {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        opts.setAllowUnicode(true);
        opts.setIndent(2);

        Yaml yaml = new Yaml(opts);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("script", buildScriptMap(script));
        return yaml.dump(root);
    }

    private Map<String, Object> buildScriptMap(Script script) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", script.getTitle());
        map.put("novel_source", script.getNovelSource());
        map.put("generated_at", script.getGeneratedAt());

        map.put("characters", script.getCharacters().stream().map(c -> {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("id", c.getId());
            cm.put("name", c.getName());
            cm.put("description", c.getDescription());
            return cm;
        }).toList());

        map.put("scenes", script.getScenes().stream().map(s -> {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("scene_id", s.getSceneId());
            sm.put("title", s.getTitle());
            sm.put("location", s.getLocation());
            sm.put("time_of_day", s.getTimeOfDay());
            sm.put("elements", s.getElements().stream().map(e -> {
                Map<String, Object> em = new LinkedHashMap<>();
                em.put("type", e.getType().name());
                if (e.getCharacter() != null) em.put("character", e.getCharacter());
                em.put("content", e.getContent());
                if (e.getDirection() != null) em.put("direction", e.getDirection());
                return em;
            }).toList());
            return sm;
        }).toList());

        return map;
    }
}
