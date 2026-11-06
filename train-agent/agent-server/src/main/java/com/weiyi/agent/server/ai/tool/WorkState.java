package com.weiyi.agent.server.ai.tool;

import lombok.Data;

/**
 * 会话工作记忆——顶层状态（Redis 键见 RedisKeys.memoryState）。
 * 属于用户使用 AI 功能的时段而非会话持久属性：TTL 滑动续期，过期整体失效回 IDLE。
 */
@Data
public class WorkState {

    /** 顶层 FSM：IDLE / CHAT / QUERY_FILLING / BUY_AGENT / REFUND_AGENT */
    private String state = "IDLE";

    /** 分支内子状态：SLOT_COLLECTING / WAIT_CONFIRM / EXECUTING 等 */
    private String agentSub;

    /** 连续失败轮计数（达到阈值强制回 IDLE） */
    private Integer failCount = 0;
}
