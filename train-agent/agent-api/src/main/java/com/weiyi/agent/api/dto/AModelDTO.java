package com.weiyi.agent.api.dto;

import lombok.Data;

@Data
public class AModelDTO {
    private Long id;

    private String modelName;

    /** 模型类型：1文字 2向量 */
    private Integer modelType;

    private String provider;

    private String baseUrl;

    /** 上游格式：1 OpenAI兼容-chat 2 OpenAI兼容-Responses 3 Anthropic 4 Gemini */
    private Integer apiFormat;

    /** 认证方式：1 Bearer头 2 Key头直传 3 查询参数 */
    private Integer authType;

    /** 认证字段名 */
    private String authKeyName;

    private String apiModelId;

    /**
     * 掩码回显（后端生成的明文尾 4 位，如 "••••d41f"）：管理面出参不携带完整 key，
     * 配置人凭尾 4 位核对是否为预期 key。
     */
    private String apiKeyMasked;

    private Long presetId;

    /** 默认参数（JSON 字符串） */
    private String params;

    private String remark;

    /** 状态：1启用 2停用 */
    private Integer status;
}
