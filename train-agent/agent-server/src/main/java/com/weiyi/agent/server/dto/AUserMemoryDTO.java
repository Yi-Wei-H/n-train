package com.weiyi.agent.server.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 记忆条目展示（来源/版本等元数据仅在管理页展示，不注入提示词） */
@Data
public class AUserMemoryDTO {

    private Long id;

    private String memoryKey;

    private Integer valueMode;

    private String category;

    private String content;

    private String sourceType;

    private BigDecimal confidence;

    private Integer status;

    private Integer version;

    private LocalDateTime lastConfirmedAt;

    private LocalDateTime updatedAt;
}
