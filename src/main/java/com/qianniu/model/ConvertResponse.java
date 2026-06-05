package com.qianniu.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConvertResponse {
    private boolean success;
    private String yaml;
    private Screenplay screenplay;
    private String message;

    public static ConvertResponse ok(String yaml, Screenplay screenplay) {
        return new ConvertResponse(true, yaml, screenplay, null);
    }

    public static ConvertResponse error(String message) {
        return new ConvertResponse(false, null, null, message);
    }
}
