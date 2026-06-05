package com.qianniu.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Screenplay {
    private ScreenplayMetadata metadata;
    private List<Character> characters;
    private List<Chapter> chapters;
}
