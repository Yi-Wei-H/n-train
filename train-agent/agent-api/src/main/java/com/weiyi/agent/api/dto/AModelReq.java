package com.weiyi.agent.api.dto;

import tools.jackson.databind.annotation.JsonDeserialize;
import com.weiyi.common.core.util.AesDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AModelReq {
    private Long id;

    private String modelName;

    /** 模型类型：1文字 2向量 */
    @NotNull(message = "模型类型不能为空")
    private Integer modelType;

    @NotBlank(message = "厂商名不能为空")
    private String provider;

    @NotBlank(message = "上游地址不能为空")
    private String baseUrl;

    /** 上游格式：1 OpenAI兼容-chat 2 OpenAI兼容-Responses 3 Anthropic 4 Gemini */
    @NotNull(message = "上游格式不能为空")
    private Integer apiFormat;

    /** 认证方式：1 Bearer头 2 Key头直传 3 查询参数 */
    @NotNull(message = "认证类型不能为空")
    private Integer authType;

    /** 认证字段名：auth_type=2/3 时必填（服务层校验），=1 时忽略并清空 */
    private String authKeyName;

    @NotBlank(message = "模型ID不能为空")
    private String apiModelId;

    /** 新增必填（服务层校验）；更新传空表示沿用原值（更换 KEY 走独立接口） */
    @Size(min = 16, max = 200, message = "API KEY 长度须在 16~200 之间")
    @JsonDeserialize(using = AesDeserializer.class)
    private String apiKey;

    private Long presetId;

    /** 默认参数（JSON 字符串，如 {"temperature":0.7}），空则用厂商默认 */
    private String params;

    private String remark;

    /** 传 null 表示不修改；新增时忽略（固定下架）；更新传 1（启用）时校验向量模型唯一 */
    private Integer status;
}
