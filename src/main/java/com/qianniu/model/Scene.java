package com.qianniu.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Scene {
    private String sceneId;
    private String title;
    private String location;
    private String timeOfDay;
    private List<SceneElement> elements;
}
