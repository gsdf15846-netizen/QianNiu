package com.qianniu.model;

import lombok.Data;

@Data
public class ConvertRequest {
    private String title;
    private String text;
    /** 可选，提供后从该 conversionId 的中断点继续，跳过已完成的章节 */
    private String conversionId;
}
