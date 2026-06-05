package com.qianniu.controller;

import com.qianniu.model.ConvertRequest;
import com.qianniu.model.ConvertResponse;
import com.qianniu.service.NovelConverterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
public class ConvertController {

    private final NovelConverterService converterService;

    public ConvertController(NovelConverterService converterService) {
        this.converterService = converterService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "novel-to-script"));
    }

    @PostMapping("/convert")
    public ResponseEntity<ConvertResponse> convert(@RequestBody ConvertRequest request) {
        if (request.getText() == null || request.getText().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ConvertResponse.error("小说文本不能为空"));
        }
        log.info("Converting novel, title={}, length={}", request.getTitle(), request.getText().length());
        ConvertResponse response = converterService.convert(
                request.getTitle() != null ? request.getTitle() : "未命名",
                request.getText()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/upload")
    public ResponseEntity<ConvertResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ConvertResponse.error("请上传文件"));
        }
        try {
            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            String resolvedTitle = title != null ? title : file.getOriginalFilename();
            log.info("Processing uploaded file={}, size={}", file.getOriginalFilename(), file.getSize());
            return ResponseEntity.ok(converterService.convert(resolvedTitle, text));
        } catch (IOException e) {
            log.error("File read error", e);
            return ResponseEntity.internalServerError()
                    .body(ConvertResponse.error("文件读取失败：" + e.getMessage()));
        }
    }
}
