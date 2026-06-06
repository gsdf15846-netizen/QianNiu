package com.qianniu.controller;

import com.qianniu.model.ConversionHistory;
import com.qianniu.model.ConvertRequest;
import com.qianniu.model.ConvertResponse;
import com.qianniu.repository.ConversionHistoryRepository;
import com.qianniu.service.NovelConverterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api")
public class ConvertController {

    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final NovelConverterService converterService;
    private final ConversionHistoryRepository historyRepo;

    public ConvertController(NovelConverterService converterService,
                             ConversionHistoryRepository historyRepo) {
        this.converterService = converterService;
        this.historyRepo = historyRepo;
    }

    // ─── Health ────────────────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "novel-to-script"));
    }

    // ─── Convert (JSON body) ───────────────────────────────────────────────────

    @PostMapping("/convert")
    public ResponseEntity<ConvertResponse> convert(@RequestBody ConvertRequest request) {
        if (request.getText() == null || request.getText().isBlank()) {
            return ResponseEntity.badRequest().body(ConvertResponse.error("小说文本不能为空"));
        }
        String title = request.getTitle() != null ? request.getTitle() : "未命名";
        log.info("Convert request: title={}, length={}, conversionId={}",
                title, request.getText().length(), request.getConversionId());
        return ResponseEntity.ok(converterService.convert(title, request.getText(), request.getConversionId()));
    }

    // ─── Upload (multipart) ────────────────────────────────────────────────────

    @PostMapping("/upload")
    public ResponseEntity<ConvertResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "conversionId", required = false) String conversionId) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ConvertResponse.error("请上传文件"));
        }
        try {
            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            String resolvedTitle = title != null ? title : file.getOriginalFilename();
            log.info("Upload: file={}, size={}, conversionId={}", file.getOriginalFilename(), file.getSize(), conversionId);
            return ResponseEntity.ok(converterService.convert(resolvedTitle, text, conversionId));
        } catch (IOException e) {
            log.error("File read error", e);
            return ResponseEntity.internalServerError().body(ConvertResponse.error("文件读取失败：" + e.getMessage()));
        }
    }

    // ─── History list ──────────────────────────────────────────────────────────

    @GetMapping("/history")
    public ResponseEntity<List<HistorySummary>> listHistory() {
        List<HistorySummary> list = historyRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(h -> new HistorySummary(
                        h.getId(),
                        h.getConversionId(),
                        h.getTitle(),
                        h.getCreatedAt() != null ? h.getCreatedAt().format(DISPLAY_FMT) : "",
                        h.getTotalChapters(),
                        h.getCompletedChapters(),
                        h.getStatus()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // ─── History detail ────────────────────────────────────────────────────────

    @GetMapping("/history/{id}")
    public ResponseEntity<?> getHistory(@PathVariable Long id) {
        return historyRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ─── Delete history ────────────────────────────────────────────────────────

    @DeleteMapping("/history/{id}")
    public ResponseEntity<Void> deleteHistory(@PathVariable Long id) {
        if (!historyRepo.existsById(id)) return ResponseEntity.notFound().build();
        historyRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ─── DTO ───────────────────────────────────────────────────────────────────

    record HistorySummary(Long id, String conversionId, String title, String createdAt,
                          Integer totalChapters, Integer completedChapters, String status) {}
}
