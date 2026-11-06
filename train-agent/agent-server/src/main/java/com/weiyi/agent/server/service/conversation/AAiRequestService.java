package com.weiyi.agent.server.service.conversation;

import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.server.entity.AAiRequest;
import com.weiyi.agent.server.mapper.AAiRequestMapper;
import com.weiyi.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 请求回执写入：忙拒绝回执必须独立事务提交——调用方（定位事务）随后抛业务异常回滚，
 * REJECTED 记录不能随之丢失；同类自调用不走代理，故独立成 Bean。
 */
@Service
@RequiredArgsConstructor
public class AAiRequestService {

    private final AAiRequestMapper requestMapper;

    /** 忙拒绝回执：新事务提交（user_id + client_request_id 唯一，重复插入由约束兜底） */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void insertRejected(Long userId, String clientRequestId) {
        AAiRequest receipt = new AAiRequest();
        receipt.setUserId(userId);
        receipt.setClientRequestId(clientRequestId);
        receipt.setRequestStatus(AgentConstants.AI_REQUEST_REJECTED);
        receipt.setErrMsg(ErrorCode.AI_REPLY_IN_PROGRESS.getMessage());
        requestMapper.insert(receipt);
    }
}
