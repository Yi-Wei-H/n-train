package com.weiyi.agent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AModelPresetReq {
    private Long id;
    @NotBlank(message = "厂商不能为空")
    private String vendor;
    @NotBlank(message = "预设名不能为空")
    private String presetName;
    @NotNull(message = "模型类型不能为空")
    private Integer modelType;
    @NotBlank(message = "上游地址不能为空")
    private String baseUrl;
    @NotNull(message = "上游格式不能为空")
    private Integer apiFormat;
    @NotNull(message = "认证类型不能为空")
    private Integer authType;
    private String authKeyName;
    @NotBlank(message = "模型ID不能为空")
    private String apiModelId;
    private String defaultParams = "{\"temperature\":0.2}";
    private String description;
    private Integer status;
}
