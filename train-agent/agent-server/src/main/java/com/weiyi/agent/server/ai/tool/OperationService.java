package com.weiyi.agent.server.ai.tool;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiyi.agent.server.config.AgentProperties;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.server.entity.AOperation;
import com.weiyi.agent.server.mapper.AOperationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AI 写操作状态机（a_ai_operation）：预检建 PENDING、确认轮 CAS 消费、终态回写。
 * 本表是确认有效性的权威，Redis 待确认单仅为镜像。
 */
@Service
@RequiredArgsConstructor
public class OperationService {

    private final AOperationMapper operationMapper;
    private final AgentProperties agentProperties;

    /**
     * 创建待确认操作：同事务将同会话同类型的旧 PENDING 一律作废（新预检覆盖）。
     * 幂等键 = operationId，member 写接口按此去重。
     */
    @Transactional
    public AOperation createPending(Long userId, Long conversationId, int type,
                                    String summaryText, String summarySnapshot) {
        operationMapper.update(null, Wrappers.<AOperation>lambdaUpdate()
                .set(AOperation::getStatus, AgentConstants.OPERATION_STATUS_CANCELLED)
                .set(AOperation::getErrorCode, "OVERWRITTEN")
                .eq(AOperation::getUserId, userId)
                .eq(AOperation::getConversationId, conversationId)
                .eq(AOperation::getType, type)
                .eq(AOperation::getStatus, AgentConstants.OPERATION_STATUS_PENDING));
        long id = IdWorker.getId();
        AOperation operation = new AOperation();
        operation.setId(id);
        operation.setUserId(userId);
        operation.setConversationId(conversationId);
        operation.setType(type);
        operation.setConfirmId(UUID.randomUUID().toString().replace("-", ""));
        operation.setSummaryText(summaryText);
        operation.setSummarySnapshot(summarySnapshot);
        operation.setSummaryVersion(1);
        operation.setIdempotencyKey(String.valueOf(id));
        operation.setStatus(AgentConstants.OPERATION_STATUS_PENDING);
        operation.setExpiresAt(LocalDateTime.now().plusMinutes(agentProperties.getConfirm().getTtlMinutes()));
        operation.setRecoveryAttempts(0);
        operationMapper.insert(operation);
        return operation;
    }

    /** 确认令牌一次性消费：PENDING → EXECUTING（CAS，失败即已被处理） */
    public boolean casToExecuting(Long operationId, Duration lease) {
        return operationMapper.update(null, Wrappers.<AOperation>lambdaUpdate()
                .set(AOperation::getStatus, AgentConstants.OPERATION_STATUS_EXECUTING)
                .set(AOperation::getConsumedAt, LocalDateTime.now())
                .set(AOperation::getExecutionStartedAt, LocalDateTime.now())
                .set(AOperation::getExecutionLeaseUntil, LocalDateTime.now().plus(lease))
                .eq(AOperation::getId, operationId)
                .eq(AOperation::getStatus, AgentConstants.OPERATION_STATUS_PENDING)) > 0;
    }

    public void markSuccess(Long operationId, String bizNo) {
        operationMapper.update(null, Wrappers.<AOperation>lambdaUpdate()
                .set(AOperation::getStatus, AgentConstants.OPERATION_STATUS_SUCCESS)
                .set(AOperation::getBizNo, bizNo)
                .eq(AOperation::getId, operationId));
    }

    public void markFailed(Long operationId, String errorCode) {
        operationMapper.update(null, Wrappers.<AOperation>lambdaUpdate()
                .set(AOperation::getStatus, AgentConstants.OPERATION_STATUS_FAILED)
                .set(AOperation::getErrorCode, errorCode)
                .eq(AOperation::getId, operationId));
    }

    /** 调用超时/结果不明：不盲重试，交由对账恢复 */
    public void markUnknown(Long operationId) {
        operationMapper.update(null, Wrappers.<AOperation>lambdaUpdate()
                .set(AOperation::getStatus, AgentConstants.OPERATION_STATUS_UNKNOWN)
                .eq(AOperation::getId, operationId));
    }

    public void cancel(Long operationId, String errorCode) {
        operationMapper.update(null, Wrappers.<AOperation>lambdaUpdate()
                .set(AOperation::getStatus, AgentConstants.OPERATION_STATUS_CANCELLED)
                .set(AOperation::getErrorCode, errorCode)
                .eq(AOperation::getId, operationId)
                .eq(AOperation::getStatus, AgentConstants.OPERATION_STATUS_PENDING));
    }

    /** 读取仍可确认的待确认单；不存在/非 PENDING/已过期均返回 null（过期顺手置 EXPIRED） */
    public AOperation loadActivePending(Long operationId) {
        AOperation operation = operationMapper.selectById(operationId);
        if (operation == null || operation.getStatus() != AgentConstants.OPERATION_STATUS_PENDING) {
            return null;
        }
        return ensureNotExpired(operation);
    }

    /**
     * 按身份反查会话内唯一活跃待确认单（确认入口用）：前端按钮只传意图，
     * 操作 ID 不出服务端；createPending 已保证同会话同类型至多一个 PENDING，倒序取最新兜底。
     */
    public AOperation loadPendingByConversation(Long userId, Long conversationId) {
        AOperation operation = operationMapper.selectOne(Wrappers.<AOperation>lambdaQuery()
                .eq(AOperation::getUserId, userId)
                .eq(AOperation::getConversationId, conversationId)
                .eq(AOperation::getStatus, AgentConstants.OPERATION_STATUS_PENDING)
                .orderByDesc(AOperation::getId)
                .last("LIMIT 1"));
        return operation == null ? null : ensureNotExpired(operation);
    }

    /** 过期顺手置 EXPIRED 并视为不可确认 */
    private AOperation ensureNotExpired(AOperation operation) {
        if (operation.getExpiresAt() != null && operation.getExpiresAt().isBefore(LocalDateTime.now())) {
            operationMapper.update(null, Wrappers.<AOperation>lambdaUpdate()
                    .set(AOperation::getStatus, AgentConstants.OPERATION_STATUS_EXPIRED)
                    .eq(AOperation::getId, operation.getId())
                    .eq(AOperation::getStatus, AgentConstants.OPERATION_STATUS_PENDING));
            return null;
        }
        return operation;
    }
}
