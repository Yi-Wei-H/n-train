package com.weiyi.agent.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AUserModelReq {

    @NotNull(message = "用户 id 不能为空")
    private Long userId;

    /** 状态：1可用 2禁用；非空覆盖 */
    private Integer status;

    /** 每日调用上限（次数）：0=不限；非空覆盖 */
    private Integer dailyQuota;
}
