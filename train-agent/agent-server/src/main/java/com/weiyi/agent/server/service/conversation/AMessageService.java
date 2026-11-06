package com.weiyi.agent.server.service.conversation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.api.dto.AMessageDTO;
import com.weiyi.agent.api.dto.AMessageQuery;
import com.weiyi.agent.server.req.AMessageReq;
import com.weiyi.agent.server.entity.AMessage;
import com.weiyi.agent.server.mapper.AMessageMapper;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.result.PageResult;
import com.weiyi.common.core.util.BeanCopyUtils;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.common.mybatis.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AMessageService {

    /** 触发方白名单（对齐 DDL 注释：user 用户 / assistant AI / system 系统自动） */
    private static final Set<String> ROLES = Set.of(
            AgentConstants.MESSAGE_ROLE_USER,
            AgentConstants.MESSAGE_ROLE_ASSISTANT,
            AgentConstants.MESSAGE_ROLE_SYSTEM);

    private final AMessageMapper messageMapper;

    /**
     * 管理端历史轮次查询：按 id 倒序——雪花 id 时间单调，倒序即最新一轮在前（管理页从最近消息查起）。
     * 不传 role 默认排除 system 轮；显式传 role 精确筛选（管理侧查系统轮）。
     */
    public PageResult<AMessageDTO> list(AMessageQuery query) {
        Page<AMessage> page = messageMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                Wrappers.<AMessage>lambdaQuery()
                        .eq(query.getConversationId() != null, AMessage::getConversationId, query.getConversationId())
                        .eq(query.getChatModelId() != null, AMessage::getChatModelId, query.getChatModelId())
                        .eq(query.getRole() != null, AMessage::getRole, query.getRole())
                        .ne(query.getRole() == null, AMessage::getRole, AgentConstants.MESSAGE_ROLE_SYSTEM)
                        .orderByDesc(AMessage::getId));
        return PageUtils.convert(page, message -> BeanCopyUtils.copy(message, AMessageDTO.class));
    }

    /**
     * 历史分页窗口：id 倒序取 beforeId（不含，为空取最新）之前的最多 limit 条，返回新→旧，
     * 调用方多取一条判断是否还有更早；排除 system 轮，贴 idx_msg_conv(conversation_id, id)。
     */
    public List<AMessage> descBefore(Long conversationId, Long beforeId, int limit) {
        return messageMapper.selectList(Wrappers.<AMessage>lambdaQuery()
                .eq(AMessage::getConversationId, conversationId)
                .ne(AMessage::getRole, AgentConstants.MESSAGE_ROLE_SYSTEM)
                .lt(beforeId != null, AMessage::getId, beforeId)
                .orderByDesc(AMessage::getId)
                .last("LIMIT " + limit));
    }

    /** 最近 count 轮消息（时间正序返回）：id 倒序取一页再反转，排除 system 轮，供聊天上下文与标题素材使用 */
    public List<AMessageDTO> latest(Long conversationId, int count) {
        List<AMessage> records = messageMapper.selectPage(new Page<>(1, count),
                        Wrappers.<AMessage>lambdaQuery()
                                .eq(AMessage::getConversationId, conversationId)
                                .ne(AMessage::getRole, AgentConstants.MESSAGE_ROLE_SYSTEM)
                                .orderByDesc(AMessage::getId))
                .getRecords();
        List<AMessageDTO> messages = new ArrayList<>(records.size());
        for (int i = records.size() - 1; i >= 0; i--) {
            messages.add(BeanCopyUtils.copy(records.get(i), AMessageDTO.class));
        }
        return messages;
    }

    /** 最近 count 行消息按 "role:content" 正序拼为提示词素材文本（聊天上下文与标题生成共用口径，排除 system 轮） */
    public String latestAsText(Long conversationId, int count) {
        StringBuilder sb = new StringBuilder();
        for (AMessageDTO message : latest(conversationId, count)) {
            if (message.getUserContent() != null) {
                sb.append(AgentConstants.MESSAGE_ROLE_USER).append(":").append(message.modelContent()).append("\n");
            }
            if (message.getReply() != null) {
                sb.append(AgentConstants.MESSAGE_ROLE_ASSISTANT).append(":").append(message.getReply()).append("\n");
            }
        }
        return sb.toString();
    }

    /** 会话首条用户轮（标题素材来源：卡片轮的可见主题由标题服务解析），无用户轮返回 null */
    public AMessage firstUserRound(Long conversationId) {
        return messageMapper.selectOne(Wrappers.<AMessage>lambdaQuery()
                .eq(AMessage::getConversationId, conversationId)
                .eq(AMessage::getRole, AgentConstants.MESSAGE_ROLE_USER)
                .orderByAsc(AMessage::getId)
                .last("LIMIT 1"));
    }

    /** 按 id 取消息（任务回查），不存在返回 null */
    public AMessage getById(Long id) {
        return messageMapper.selectById(id);
    }

    /** 新增一轮消息（一条=一轮对话，表不可变只 INSERT），返回消息 id */
    public Long create(AMessageReq req) {
        BizAssert.isTrue(ROLES.contains(req.getRole()), ErrorCode.PARAM_ERROR,
                "触发方只允许 user/assistant/system");
        if (AgentConstants.MESSAGE_ROLE_USER.equals(req.getRole())) {
            BizAssert.isTrue(req.getUserContent() != null || req.getCardId() != null, ErrorCode.PARAM_ERROR,
                    "用户轮须有输入内容或卡片");
        }
        AMessage message = BeanCopyUtils.copy(req, AMessage.class);
        messageMapper.insert(message);
        log.info("轮次消息已记录：conversationId={}, role={}, id={}", message.getConversationId(), message.getRole(), message.getId());
        return message.getId();
    }
}
