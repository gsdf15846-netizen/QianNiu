package com.qianniu.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianniu.model.Character;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversionCacheService {

    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public record ConversionState(List<Character> characters, List<String> synopses) {}

    // ─── Init ──────────────────────────────────────────────────────────────────

    public void initState(String convId, String title, int totalChapters) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("title", title);
        meta.put("totalChapters", totalChapters);
        meta.put("completedChapters", 0);
        set(metaKey(convId), meta);
        saveState(convId, new ArrayList<>(), new ArrayList<>());
    }

    // ─── Existence / progress ──────────────────────────────────────────────────

    public boolean exists(String convId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(metaKey(convId)));
    }

    public void updateProgress(String convId, int completed) {
        try {
            String raw = redisTemplate.opsForValue().get(metaKey(convId));
            if (raw != null) {
                Map<String, Object> meta = objectMapper.readValue(raw, Map.class);
                meta.put("completedChapters", completed);
                set(metaKey(convId), meta);
            }
        } catch (Exception e) {
            log.warn("Redis updateProgress failed: {}", e.getMessage());
        }
    }

    // ─── Chapter scenes ────────────────────────────────────────────────────────

    public boolean isChapterCached(String convId, int chapterNum) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(chapterKey(convId, chapterNum)));
    }

    public void saveChapterScenes(String convId, int chapterNum, List<Map<String, Object>> sceneMaps) {
        set(chapterKey(convId, chapterNum), sceneMaps);
    }

    public List<Map<String, Object>> loadChapterScenes(String convId, int chapterNum) {
        try {
            String raw = redisTemplate.opsForValue().get(chapterKey(convId, chapterNum));
            if (raw == null) return null;
            return objectMapper.readValue(raw, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("Redis loadChapterScenes failed: {}", e.getMessage());
            return null;
        }
    }

    // ─── Accumulated state (characters + synopses) ─────────────────────────────

    public void saveState(String convId, List<Character> characters, List<String> synopses) {
        Map<String, Object> state = Map.of("characters", characters, "synopses", synopses);
        set(stateKey(convId), state);
    }

    @SuppressWarnings("unchecked")
    public ConversionState loadState(String convId) {
        try {
            String raw = redisTemplate.opsForValue().get(stateKey(convId));
            if (raw == null) return null;
            Map<String, Object> state = objectMapper.readValue(raw, Map.class);

            List<Map<String, Object>> charMaps = (List<Map<String, Object>>)
                    state.getOrDefault("characters", Collections.emptyList());
            List<String> synopses = (List<String>)
                    state.getOrDefault("synopses", Collections.emptyList());

            List<Character> characters = charMaps.stream().map(m -> new Character(
                    (String) m.getOrDefault("id", ""),
                    (String) m.getOrDefault("name", ""),
                    (List<String>) m.getOrDefault("aliases", Collections.emptyList()),
                    (String) m.getOrDefault("description", "")
            )).collect(Collectors.toList());

            return new ConversionState(characters, new ArrayList<>(synopses));
        } catch (Exception e) {
            log.warn("Redis loadState failed: {}", e.getMessage());
            return null;
        }
    }

    // ─── Internals ─────────────────────────────────────────────────────────────

    private void set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), TTL);
        } catch (Exception e) {
            log.error("Redis write failed for key {}: {}", key, e.getMessage());
        }
    }

    private String metaKey(String convId)          { return "conv:" + convId + ":meta"; }
    private String stateKey(String convId)         { return "conv:" + convId + ":state"; }
    private String chapterKey(String convId, int n){ return "conv:" + convId + ":ch:" + n; }
}
