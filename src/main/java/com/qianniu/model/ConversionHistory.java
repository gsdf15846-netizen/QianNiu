package com.qianniu.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "conversion_history")
public class ConversionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversion_id", nullable = false, unique = true, length = 36)
    private String conversionId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private Integer totalChapters;
    private Integer completedChapters;

    @Column(columnDefinition = "LONGTEXT")
    private String yamlResult;

    @Column(nullable = false, length = 20)
    private String status; // IN_PROGRESS / COMPLETED / FAILED
}
