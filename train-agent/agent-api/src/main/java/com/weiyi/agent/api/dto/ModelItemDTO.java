package com.weiyi.agent.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 归一后的上游模型条目：id 即 api_model_id 的候选值。
 */
@Data
@AllArgsConstructor
public class ModelItemDTO {

    private String id;

    /** 可空：OpenAI 系有（如 system/openai），Anthropic 系恒空 */
    private String ownedBy;

    /** 可空：仅 Anthropic 系有（display_name） */
    private String displayName;
}
