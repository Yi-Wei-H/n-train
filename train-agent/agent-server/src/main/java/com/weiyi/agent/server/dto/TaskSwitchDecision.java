package com.weiyi.agent.server.dto;

/**
 * 任务切换判定结构化输出：仅 CONTINUE / SWITCH / SIDE 一个字段。
 */
public record TaskSwitchDecision(String decision) {
}
