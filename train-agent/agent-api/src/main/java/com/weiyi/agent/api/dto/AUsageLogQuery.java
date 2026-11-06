package com.weiyi.agent.api.dto;

import com.weiyi.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AUsageLogQuery extends PageQuery {

    /** 用户 id（可选筛选） */
    private Long userId;

    /** 模型 id（可选筛选） */
    private Long modelId;

    /** 会话 id（可选筛选） */
    private Long conversationId;

    /** 调用结果：1成功 2失败（可选筛选） */
    private Integer callStatus;
}
