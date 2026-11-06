package com.weiyi.member.service;

import com.weiyi.agent.api.api.AgentUserModelApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 会员注册后的 AI 授权行初始化（agent 域 createByUserId，幂等）。
 * 授权行属于附属数据：注册事务提交后再调用，失败只告警——
 * agent 侧读路径（聊天/页面渲染）发现缺行会按默认值补建，不影响注册结果。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentUserModelProvisioner {

    private final AgentUserModelApi agentUserModelApi;

    /** 注册事务提交后初始化；无事务上下文时直接调用 */
    public void provisionAfterCommit(Long memberId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    provision(memberId);
                }
            });
            return;
        }
        provision(memberId);
    }

    private void provision(Long memberId) {
        try {
            agentUserModelApi.createByUserId(memberId);
        } catch (Exception e) {
            log.warn("AI 授权行初始化失败（不影响注册，agent 侧读路径会补建）：memberId={}: {}",
                    memberId, e.getMessage());
        }
    }
}
