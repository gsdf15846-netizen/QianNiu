package com.qianniu.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScreenplayMetadata {
    private String title;
    private String sourceNovel;
    private String author;
    private String createdAt;
    private int totalChapters;
}
