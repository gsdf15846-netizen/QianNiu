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
    private Script script;
    private String message;

    public static ConvertResponse ok(String yaml, Script script) {
        return new ConvertResponse(true, yaml, script, null);
    }

    public static ConvertResponse error(String message) {
        return new ConvertResponse(false, null, null, message);
    }
}
