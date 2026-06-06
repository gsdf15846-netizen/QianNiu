package com.qianniu.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Chapter {
    private int chapterNumber;
    private String title;
    private List<Scene> scenes;
}
