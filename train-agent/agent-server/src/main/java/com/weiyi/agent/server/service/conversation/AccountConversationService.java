package com.weiyi.agent.server.service.conversation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiyi.agent.server.ai.tool.WorkMemoryStore;
import com.weiyi.agent.server.config.AgentProperties;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.api.dto.AMessageDTO;
import com.weiyi.agent.server.dto.ChatCurrentVO;
import com.weiyi.agent.server.dto.ChatHistoryVO;
import com.weiyi.agent.server.entity.AAiRequest;
import com.weiyi.agent.server.entity.AConversation;
import com.weiyi.agent.server.entity.AMessage;
import com.weiyi.agent.server.entity.AOperation;
import com.weiyi.agent.server.entity.AUserChatState;
import com.weiyi.agent.server.entity.AUserModel;
import com.weiyi.agent.server.mapper.AAiRequestMapper;
import com.weiyi.agent.server.mapper.AConversationMapper;
import com.weiyi.agent.server.mapper.AOperationMapper;
import com.weiyi.agent.server.mapper.AUserChatStateMapper;
import com.weiyi.agent.server.service.AUsageLogService;
import com.weiyi.agent.server.service.model.AUserModelService;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.util.BeanCopyUtils;
import com.weiyi.common.core.util.BizAssert;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 账户级当前会话定位：一账户一个当前主会话，首条有效消息才创建；
 * 72 小时未交互翻篇（打开页面惰性判定 + 发送链路兜底判定，同一动作）；
 * 主回复占用位保证同账户串行；clientRequestId 回执做幂等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountConversationService {

    /** 用户端一页历史轮数（current 首屏与上滑翻页同口径） */
    private static final int HISTORY_PAGE_SIZE = 10;

    private final AUserChatStateMapper stateMapper;
    private final AAiRequestMapper requestMapper;
    private final AConversationMapper conversationMapper;
    private final AOperationMapper operationMapper;
    private final AConversationService conversationService;
    private final AMessageService messageService;
    private final AAiRequestService aiRequestService;
    private final AUserModelService userModelService;
    private final AUsageLogService usageLogService;
    private final WorkMemoryStore workMemoryStore;
    private final AgentProperties agentProperties;

    /** 发送链路定位结果 */
    @Data
    public static class Located {

        private Long conversationId;

        /** 本次发送发生翻篇（SSE meta.conversationReset=true，前端插入分隔行） */
        private boolean rotated;

        /** 本轮为会话首轮（标题已在本事务置 GENERATING，流内探测启用） */
        private boolean firstRound;

        /** 非空=clientRequestId 命中成功回执，直接重放该轮不重复执行 */
        private AMessage replay;
    }

    /**
     * 发送链路定位会话（指针行锁内串行）：回执幂等 → 忙判定（占用超时回收）→
     * 72h 翻篇/指针修复 → 复用或首建 → 写 PROCESSING 回执并占用主回复位。
     * 任一步失败抛业务异常（推流前的 Result 错误），事务回滚不留 PROCESSING。
     */
    @Transactional(rollbackFor = Exception.class)
    public Located locateForSend(Long userId, String clientRequestId, Long chatModelId) {
        Located located = new Located();

        // 1. 回执幂等：同 clientRequestId 不重复建会话、不重复执行
        AAiRequest existing = selectReceipt(userId, clientRequestId);
        if (existing != null) {
            if (existing.getRequestStatus() == AgentConstants.AI_REQUEST_SUCCESS
                    && existing.getMessageId() != null) {
                AMessage replay = messageService.getById(existing.getMessageId());
                if (replay != null) {
                    located.setReplay(replay);
                    located.setConversationId(existing.getConversationId());
                    return located;
                }
                log.warn("回执成功但消息行缺失（数据异常，按已结束处理）：userId={} clientRequestId={}",
                        userId, clientRequestId);
            }
            if (existing.getRequestStatus() == AgentConstants.AI_REQUEST_PROCESSING) {
                throw new BusinessException(ErrorCode.AI_REPLY_IN_PROGRESS);
            }
            throw new BusinessException(ErrorCode.AI_REQUEST_FINISHED);
        }

        // 2. 锁定账户指针行（并发首聊/翻篇/占用在行锁上串行）
        AUserChatState state = lockState(userId);

        // 3. 忙判定：占用未超时拒绝（REJECTED 回执独立事务落库，不随异常回滚）；超时回收孤儿占用
        if (state.getActiveRequestId() != null) {
            if (!busyExpired(state)) {
                aiRequestService.insertRejected(userId, clientRequestId);
                throw new BusinessException(ErrorCode.AI_REPLY_IN_PROGRESS);
            }
            recoverOrphanOccupation(state);
        }

        // 4. 当前会话检查：指针失效修复 / 72h 翻篇（兜底路径，页面停留不刷新直接发送）
        boolean rotated = false;
        Long conversationId = state.getCurrentConversationId();
        if (conversationId != null) {
            AConversation conversation = conversationMapper.selectById(conversationId);
            if (conversation == null || conversation.getStatus() != AgentConstants.STATUS_ON) {
                conversationId = null; // 旧会话已被归档/删除：指针修复，本轮首建
            } else if (idleExpired(conversation)) {
                archiveConversation(conversation);
                conversationId = null;
                rotated = true;
            }
        }

        // 5. 复用或首建；首轮在建会话事务内触发标题任务（NONE→GENERATING 只此一次）
        boolean firstRound = false;
        if (conversationId == null) {
            conversationId = conversationService.createFirstConversation(userId, chatModelId);
            conversationService.markTitleGenerating(conversationId);
            firstRound = true;
        } else {
            conversationService.touchLastInteraction(conversationId);
        }

        // 6. 占用主回复位 + PROCESSING 回执（与定位同事务：任一步失败整体回滚）
        occupy(state, conversationId, clientRequestId);
        AAiRequest receipt = new AAiRequest();
        receipt.setUserId(userId);
        receipt.setClientRequestId(clientRequestId);
        receipt.setConversationId(conversationId);
        receipt.setRequestStatus(AgentConstants.AI_REQUEST_PROCESSING);
        requestMapper.insert(receipt);

        located.setConversationId(conversationId);
        located.setRotated(rotated);
        located.setFirstRound(firstRound);
        return located;
    }

    /**
     * 页面打开渲染数据：惰性翻篇判定（超时归档 + 清指针，不建新会话）+
     * 指针/标题/占用/禁用原因/消息组装。只读路径仅翻篇时写库。
     */
    public ChatCurrentVO currentView(Long userId) {
        ChatCurrentVO vo = new ChatCurrentVO();
        fillDisabled(userId, vo);

        AUserChatState state = stateMapper.selectById(userId);
        Long conversationId = state != null ? state.getCurrentConversationId() : null;
        AConversation conversation = conversationId == null ? null : conversationMapper.selectById(conversationId);

        if (conversation == null || conversation.getStatus() != AgentConstants.STATUS_ON) {
            // 无当前会话：指针指向失效会话时顺手修复；翻篇旧记录只读返回（archived 标记）
            if (conversationId != null) {
                clearPointer(userId, conversationId);
            }
            AConversation lastArchived = conversationMapper.selectOne(Wrappers.<AConversation>lambdaQuery()
                    .eq(AConversation::getUserId, userId)
                    .eq(AConversation::getStatus, AgentConstants.STATUS_OFF)
                    .orderByDesc(AConversation::getLastMessageAt)
                    .orderByDesc(AConversation::getId)
                    .last("LIMIT 1"));
            if (lastArchived != null) {
                vo.setArchived(true);
                // 带上会话 id：旧记录只读展示，上滑翻页仍按该会话取更早轮次
                vo.setConversationId(lastArchived.getId());
                fillHistory(vo, lastArchived.getId());
            }
            return vo;
        }

        if (idleExpired(conversation)) {
            archiveConversation(conversation);
            clearPointer(userId, conversation.getId());
            vo.setArchived(true);
            vo.setConversationId(conversation.getId());
            fillHistory(vo, conversation.getId());
            return vo;
        }

        vo.setConversationId(conversation.getId());
        vo.setTitle(conversation.getTitle());
        vo.setTitleStatus(AgentConstants.titleStatusName(conversation.getTitleStatus()));
        vo.setChatModelId(conversation.getChatModelId());
        vo.setActiveRequest(state.getActiveRequestId() != null && !busyExpired(state));
        fillHistory(vo, conversation.getId());
        return vo;
    }

    /**
     * 请求收尾（流结束/失败回调，chatExecutor 线程）：回执置 SUCCESS（关联 messageId）/ FAILED，
     * 并 CAS 释放主回复占用——只清自己的占用，防止误清崩溃恢复后的新任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeRequest(Long userId, String clientRequestId, Long messageId, String errMsg) {
        AAiRequest receipt = selectReceipt(userId, clientRequestId);
        if (receipt == null) {
            log.warn("请求回执缺失（跳过收尾）：userId={} clientRequestId={}", userId, clientRequestId);
        } else {
            String reason = errMsg == null ? null
                    : errMsg.substring(0, Math.min(errMsg.length(), 500));
            requestMapper.update(null, Wrappers.<AAiRequest>lambdaUpdate()
                    .eq(AAiRequest::getId, receipt.getId())
                    .set(AAiRequest::getRequestStatus, errMsg == null
                            ? AgentConstants.AI_REQUEST_SUCCESS : AgentConstants.AI_REQUEST_FAILED)
                    .set(AAiRequest::getMessageId, messageId)
                    .set(AAiRequest::getErrMsg, reason)
                    .set(AAiRequest::getUpdatedAt, LocalDateTime.now()));
        }
        releaseOccupation(userId, clientRequestId);
    }

    /** 翻篇归档：CAS 置 ARCHIVED，作废该会话待确认操作，删工作记忆镜像 */
    private void archiveConversation(AConversation conversation) {
        boolean archived = conversationService.archiveIfActive(conversation.getId());
        if (!archived) {
            return;
        }
        operationMapper.update(null, Wrappers.<AOperation>lambdaUpdate()
                .eq(AOperation::getConversationId, conversation.getId())
                .eq(AOperation::getStatus, AgentConstants.OPERATION_STATUS_PENDING)
                .set(AOperation::getStatus, AgentConstants.OPERATION_STATUS_CANCELLED)
                .set(AOperation::getErrorCode, "CONVERSATION_ARCHIVED"));
        workMemoryStore.delete(conversation.getId());
        log.info("会话翻篇归档：conversationId={} userId={}", conversation.getId(), conversation.getUserId());
    }

    /** 忙拒绝回执经独立事务 Bean 写入（AAiRequestService），此处不再自调用 */
    private AAiRequest selectReceipt(Long userId, String clientRequestId) {
        return requestMapper.selectOne(Wrappers.<AAiRequest>lambdaQuery()
                .eq(AAiRequest::getUserId, userId)
                .eq(AAiRequest::getClientRequestId, clientRequestId));
    }

    /** 指针行锁：并发首聊用 ON CONFLICT DO NOTHING 安全建行（不中断事务）后等锁 */
    private AUserChatState lockState(Long userId) {
        stateMapper.insertIgnore(userId);
        return stateMapper.selectForUpdate(userId);
    }

    private void occupy(AUserChatState state, Long conversationId, String clientRequestId) {
        stateMapper.update(null, Wrappers.<AUserChatState>lambdaUpdate()
                .eq(AUserChatState::getUserId, state.getUserId())
                .set(AUserChatState::getCurrentConversationId, conversationId)
                .set(AUserChatState::getActiveRequestId, clientRequestId)
                .set(AUserChatState::getActiveSince, LocalDateTime.now())
                .set(AUserChatState::getUpdatedAt, LocalDateTime.now()));
    }

    /** 释放占用：CAS 只清自己的占用（同 id 才清），并发安全 */
    private void releaseOccupation(Long userId, String clientRequestId) {
        stateMapper.update(null, Wrappers.<AUserChatState>lambdaUpdate()
                .eq(AUserChatState::getUserId, userId)
                .eq(AUserChatState::getActiveRequestId, clientRequestId)
                .set(AUserChatState::getActiveRequestId, null)
                .set(AUserChatState::getActiveSince, null)
                .set(AUserChatState::getUpdatedAt, LocalDateTime.now()));
    }

    /** 清当前会话指针：CAS 只清仍指向该会话的指针（与并发定位互不踩踏） */
    private void clearPointer(Long userId, Long conversationId) {
        stateMapper.update(null, Wrappers.<AUserChatState>lambdaUpdate()
                .eq(AUserChatState::getUserId, userId)
                .eq(AUserChatState::getCurrentConversationId, conversationId)
                .set(AUserChatState::getCurrentConversationId, null)
                .set(AUserChatState::getUpdatedAt, LocalDateTime.now()));
    }

    /** 孤儿占用回收：释放占用位并把对应 PROCESSING 回执置 FAILED（实例异常终止兜底） */
    private void recoverOrphanOccupation(AUserChatState state) {
        releaseOccupation(state.getUserId(), state.getActiveRequestId());
        requestMapper.update(null, Wrappers.<AAiRequest>lambdaUpdate()
                .eq(AAiRequest::getUserId, state.getUserId())
                .eq(AAiRequest::getClientRequestId, state.getActiveRequestId())
                .eq(AAiRequest::getRequestStatus, AgentConstants.AI_REQUEST_PROCESSING)
                .set(AAiRequest::getRequestStatus, AgentConstants.AI_REQUEST_FAILED)
                .set(AAiRequest::getErrMsg, "主回复占用超时回收")
                .set(AAiRequest::getUpdatedAt, LocalDateTime.now()));
        log.warn("回收超时主回复占用：userId={} clientRequestId={}", state.getUserId(), state.getActiveRequestId());
    }

    private boolean busyExpired(AUserChatState state) {
        if (state.getActiveSince() == null) {
            return true; // 有占用无计时（异常数据）：按超时处理走回收
        }
        return Duration.between(state.getActiveSince(), LocalDateTime.now()).toSeconds()
                >= agentProperties.getConversation().getBusyExpireSeconds();
    }

    /** 翻篇判定：距最近有效交互超过阈值（历史数据无交互时间时回落消息时间/创建时间） */
    private boolean idleExpired(AConversation conversation) {
        LocalDateTime anchor = conversation.getLastInteractionAt() != null
                ? conversation.getLastInteractionAt()
                : conversation.getLastMessageAt() != null
                ? conversation.getLastMessageAt() : conversation.getCreatedAt();
        if (anchor == null) {
            return false;
        }
        return Duration.between(anchor, LocalDateTime.now()).toHours()
                >= agentProperties.getConversation().getIdleTimeoutHours();
    }

    /** 禁用判定：无授权行/被禁用→无权限；当日成功用量达配额→超限（文案服务端口径） */
    private void fillDisabled(Long userId, ChatCurrentVO vo) {
        AUserModel userModel = userModelService.ensureByUserId(userId);
        if (userModel == null || userModel.getStatus() == AgentConstants.STATUS_OFF) {
            vo.setDisabled(true);
            vo.setDisabledReason(ErrorCode.NO_SUPPORT_AI_CHAT.getMessage());
            return;
        }
        if (userModel.getDailyQuota() != null && userModel.getDailyQuota() != 0) {
            Integer used = usageLogService.countWithSuccessAndToday(userId);
            if (used != null && used >= userModel.getDailyQuota()) {
                vo.setDisabled(true);
                vo.setDisabledReason(ErrorCode.MODEL_USAGE_LIMIT.getMessage());
            }
        }
    }

    /**
     * 上滑翻页：按 beforeId 向更早取一页（时间正序返回）；会话必须属于当前账户，
     * 传入他人会话与不存在同样处理，不泄露存在性。
     */
    public ChatHistoryVO history(Long userId, Long conversationId, Long beforeId) {
        AConversation conversation = conversationMapper.selectById(conversationId);
        BizAssert.isTrue(conversation != null && userId.equals(conversation.getUserId()),
                ErrorCode.NOT_FOUND, "会话不存在");
        return historyPage(conversationId, beforeId);
    }

    /** 一页历史：多取一条判断 hasMore，窗口内按时间正序（旧→新）返回 */
    private ChatHistoryVO historyPage(Long conversationId, Long beforeId) {
        List<AMessage> rows = messageService.descBefore(conversationId, beforeId, HISTORY_PAGE_SIZE + 1);
        boolean hasMore = rows.size() > HISTORY_PAGE_SIZE;
        List<AMessage> window = hasMore ? rows.subList(0, HISTORY_PAGE_SIZE) : rows;
        List<AMessageDTO> messages = new ArrayList<>(window.size());
        for (int i = window.size() - 1; i >= 0; i--) {
            messages.add(BeanCopyUtils.copy(window.get(i), AMessageDTO.class));
        }
        ChatHistoryVO vo = new ChatHistoryVO();
        vo.setMessages(messages);
        vo.setHasMore(hasMore);
        return vo;
    }

    /** 首屏：最近一页轮次与是否有更早 */
    private void fillHistory(ChatCurrentVO vo, Long conversationId) {
        ChatHistoryVO page = historyPage(conversationId, null);
        vo.setMessages(page.getMessages());
        vo.setHasMore(page.getHasMore());
    }
}
