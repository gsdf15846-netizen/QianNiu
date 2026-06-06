package com.qianniu.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Scene {
    private int sceneNumber;
    private SceneHeading heading;
    private String synopsis;
    private List<SceneElement> elements;
}
