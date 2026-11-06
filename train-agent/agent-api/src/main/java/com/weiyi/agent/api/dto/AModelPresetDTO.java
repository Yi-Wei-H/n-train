package com.weiyi.agent.api.dto;

import lombok.Data;

@Data
public class AModelPresetDTO {
    private Long id;
    private String vendor;
    private String presetName;
    private Integer modelType;
    private String baseUrl;
    private Integer apiFormat;
    private Integer authType;
    private String authKeyName;
    private String apiModelId;
    private String defaultParams;
    private String description;
    private Integer status;
}
