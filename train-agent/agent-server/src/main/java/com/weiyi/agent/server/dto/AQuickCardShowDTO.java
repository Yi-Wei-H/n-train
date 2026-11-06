package com.weiyi.agent.server.dto;

import lombok.Data;

/** 用户端展示用：只暴露最小字段（不含 prompt_text） */
@Data
public class AQuickCardShowDTO {

    private Long id;

    /** 卡片标题 */
    private String cardName;

    /** 图标标识 */
    private String icon;

    /** 展示排序，小在前 */
    private Integer sortNo;
}
