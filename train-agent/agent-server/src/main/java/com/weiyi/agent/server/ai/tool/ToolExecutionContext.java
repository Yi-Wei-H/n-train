package com.weiyi.agent.server.ai.tool;

/**
 * 工具执行上下文：由分支编排方在发起分支模型调用前创建，经 toolContext 随请求传递（见 ToolContexts）。
 * 身份与会话字段不进入模型工具 schema，工具侧只从上下文取值，杜绝模型伪造身份参数。
 */
public record ToolExecutionContext(
        Long userId,
        Long conversationId,
        String requestId,
        String state,
        String agentSub) {
}
