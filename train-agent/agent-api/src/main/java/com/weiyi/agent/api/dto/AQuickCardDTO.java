package com.weiyi.agent.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 管理端卡片完整数据 */
@Data
public class AQuickCardDTO {

    private Long id;

    private String cardName;

    private String promptText;

    private String icon;

    private Integer sortNo;

    /** 状态：1上架 2下架 */
    private Integer status;

    private LocalDateTime startAt;

    private LocalDateTime endAt;
}
