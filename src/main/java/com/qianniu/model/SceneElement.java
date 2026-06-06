package com.qianniu.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SceneElement {
    /** action / dialogue / transition / note */
    private String type;
    private String character;
    private String parenthetical;
    private String content;
}
