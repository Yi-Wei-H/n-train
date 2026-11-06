package com.weiyi.agent.api.dto;

import com.weiyi.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AUserModelQuery extends PageQuery {

    /** 用户 id（可选筛选） */
    private Long userId;

    /** 状态：1可用 2禁用（可选筛选） */
    private Integer status;
}
