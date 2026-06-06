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
    /** 本次转换的唯一 ID，24h 内可用于断点续传 */
    private String conversionId;

    public static ConvertResponse ok(String yaml, Screenplay screenplay, String conversionId) {
        ConvertResponse r = new ConvertResponse();
        r.success = true;
        r.yaml = yaml;
        r.screenplay = screenplay;
        r.conversionId = conversionId;
        return r;
    }

    public static ConvertResponse error(String message) {
        ConvertResponse r = new ConvertResponse();
        r.success = false;
        r.message = message;
        return r;
    }
}
