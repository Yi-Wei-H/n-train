package com.weiyi.agent.server.ai.tool;

import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import org.springframework.ai.chat.model.ToolContext;

/**
 * 从请求级 ToolContext 取工具执行上下文。
 * 编排方经 ChatClient.prompt().toolContext(Map.of(KEY, ctx)) 随请求显式传递，
 * 工具方法以 ToolContext 参数接收，框架注入且不进模型 schema；
 * 流式下工具在 Reactor 线程执行，上下文随请求对象走、与执行线程无关——身份传递禁用 ThreadLocal。
 */
public final class ToolContexts {

    /** toolContext 映射中承载 ToolExecutionContext 的键 */
    public static final String KEY = "ctx";

    private ToolContexts() {
    }

    /** 取上下文；缺失或身份字段不全直接拒绝执行（防无身份调用） */
    public static ToolExecutionContext from(ToolContext toolContext) {
        Object value = toolContext == null ? null : toolContext.getContext().get(KEY);
        ToolExecutionContext ctx = value instanceof ToolExecutionContext execution ? execution : null;
        if (ctx == null || ctx.userId() == null || ctx.conversationId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "工具执行上下文缺失");
        }
        return ctx;
    }
}
