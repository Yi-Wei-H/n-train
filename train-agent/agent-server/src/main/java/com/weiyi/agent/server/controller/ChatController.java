package com.weiyi.agent.server.controller;

import com.weiyi.agent.server.ai.tool.ExecuteService;
import com.weiyi.agent.server.ai.tool.ToolResult;
import com.weiyi.agent.server.constant.ToolConstants;
import com.weiyi.agent.server.dto.ChatCurrentVO;
import com.weiyi.agent.server.dto.ChatHistoryVO;
import com.weiyi.agent.server.req.ChatReq;
import com.weiyi.agent.server.req.PreOrder;
import com.weiyi.agent.server.service.chat.ChatService;
import com.weiyi.agent.server.service.conversation.AccountConversationService;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.result.Result;
import com.weiyi.common.redis.ratelimit.LimitDimension;
import com.weiyi.common.redis.ratelimit.RateLimit;
import com.weiyi.common.security.context.LoginContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 管理 AI 交谈
 */
@RestController
@RequestMapping("ai")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final AccountConversationService accountConversationService;
    private final ExecuteService executeService;

    /**
     * 交谈（流式 SSE）：meta 首事件 → delta 增量 → done；流中失败走 error 事件。
     * 身份只来自网关注入的请求头（LoginContext 还原），USER 维度限流不落入匿名桶；
     * 四维限流全部严格模式：限流组件异常时拒绝而非放行。
     */
    @PostMapping("chat")
    @RateLimit(dimension = LimitDimension.USER, count = 10, interval = 60, strict = true,
            message = "AI 对话太频繁，请稍后再试")
    @RateLimit(dimension = LimitDimension.DEVICE, count = 20, interval = 60, strict = true,
            message = "AI 对话太频繁，请稍后再试")
    @RateLimit(dimension = LimitDimension.IP, count = 30, interval = 60, strict = true,
            message = "请求过于频繁，请稍后再试")
    @RateLimit(dimension = LimitDimension.GLOBAL, count = 600, interval = 60, strict = true,
            message = "AI 服务繁忙，请稍后再试")
    public SseEmitter chat(@RequestBody ChatReq chatReq) {
        Long userId = LoginContext.requireMemberId();
        return chatService.stream(userId, chatReq);
    }

    /**
     * 打开页面渲染数据：先做 72h 惰性翻篇判定（超时归档旧会话并清指针，不建新会话），
     * 再返回当前主会话消息、标题状态、主回复占用与禁用原因。
     */
    @GetMapping("conversations/current")
    public Result<ChatCurrentVO> current() {
        Long userId = LoginContext.requireMemberId();
        return Result.ok(accountConversationService.currentView(userId));
    }

    /**
     * 上滑翻页：取指定会话中 beforeId 之前的一页轮次（时间正序）。
     * 会话归属校验在服务层；不传 beforeId 等价于取最新一页。
     */
    @GetMapping("conversations/messages")
    public Result<ChatHistoryVO> history(@RequestParam Long conversationId,
                                         @RequestParam(required = false) Long beforeId) {
        Long userId = LoginContext.requireMemberId();
        return Result.ok(accountConversationService.history(userId, conversationId, beforeId));
    }

    /**
     * 预订单确认/取消（确认卡片按钮）：请结果文本随响应返回（前端按 AI 回复气泡展示），
     * 同时以 assistant 消息回填会话。
     */
    @PostMapping("order")
    @RateLimit(dimension = LimitDimension.USER, count = 10, interval = 60, strict = true,
            message = "操作过于频繁，请稍后再试")
    public Result<String> updatePreOrder(@RequestBody PreOrder preOrder) {
        Long userId = LoginContext.requireMemberId();
        boolean confirm = ToolConstants.CHAT_ORDER_CONFIRM.equals(preOrder.getAction());
        if (!confirm && !ToolConstants.CHAT_ORDER_CANCEL.equals(preOrder.getAction())) {
            return Result.fail(ErrorCode.PARAM_ERROR);
        }
        ToolResult result = executeService.confirm(preOrder.getConversationId(), userId, confirm);
        return Result.ok(result.getDisplayMessage());
    }
}
