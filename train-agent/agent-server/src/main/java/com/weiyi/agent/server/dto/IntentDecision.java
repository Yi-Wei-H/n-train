package com.weiyi.agent.server.dto;

/**
 * 顶层意图路由结构化输出：intent/confidence/reasonCode 三字段语义，
 * 枚举合法性与置信范围由服务端二次校验（schema 合法不等于业务合法）。
 */
public record IntentDecision(String intent, Double confidence, String reasonCode) {
}
