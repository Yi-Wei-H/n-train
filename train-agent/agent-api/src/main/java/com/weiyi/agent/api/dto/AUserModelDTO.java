package com.weiyi.agent.api.dto;

import lombok.Data;

@Data
public class AUserModelDTO {

    private Long id;

    private Long userId;

    /** 模型 id；空=跟随当前启用的模型 */
    private Long modelId;

    /** 状态：1可用 2禁用 */
    private Integer status;

    /** 每日调用上限（次数）：0=不限 */
    private Integer dailyQuota;
}
