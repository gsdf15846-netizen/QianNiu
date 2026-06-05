package com.qianniu.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Script {
    private String title;
    private String novelSource;
    private String generatedAt;
    private List<Character> characters;
    private List<Scene> scenes;
}
