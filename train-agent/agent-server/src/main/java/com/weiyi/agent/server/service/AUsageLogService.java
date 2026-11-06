package com.weiyi.agent.server.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.api.dto.AUsageLogDTO;
import com.weiyi.agent.api.dto.AUsageLogQuery;
import com.weiyi.agent.server.req.AUsageLogReq;
import com.weiyi.agent.server.entity.AUsageLog;
import com.weiyi.agent.server.entity.AUserModel;
import com.weiyi.agent.server.mapper.AUsageLogMapper;
import com.weiyi.agent.server.mapper.AUserModelMapper;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.result.PageResult;
import com.weiyi.common.core.util.BeanCopyUtils;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.common.mybatis.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class AUsageLogService {

    private static final int ERR_MSG_MAX_LEN = 512;

    private final AUsageLogMapper usageLogMapper;

    /** 记录一条调用流水；err_msg 超长按列宽截断，不因文案超长丢行。返回流水 id（a_message.usage_log_id 关联用） */
    public Long record(AUsageLogReq req) {
        if (req.getErrMsg() != null && req.getErrMsg().length() > ERR_MSG_MAX_LEN) {
            req.setErrMsg(req.getErrMsg().substring(0, ERR_MSG_MAX_LEN));
        }
        validate(req);
        AUsageLog usageLog = BeanCopyUtils.copy(req, AUsageLog.class);
        usageLogMapper.insert(usageLog);
        log.info("用量流水已记录：userId={}, modelId={}, callStatus={}", req.getUserId(), req.getModelId(), req.getCallStatus());
        return usageLog.getId();
    }

    /** 分页查询流水（userId / modelId / conversationId / callStatus 可选筛选，按创建时间倒序） */
    public PageResult<AUsageLogDTO> list(AUsageLogQuery query) {
        Page<AUsageLog> page = usageLogMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                Wrappers.<AUsageLog>lambdaQuery()
                        .eq(query.getUserId() != null, AUsageLog::getUserId, query.getUserId())
                        .eq(query.getModelId() != null, AUsageLog::getModelId, query.getModelId())
                        .eq(query.getConversationId() != null, AUsageLog::getConversationId, query.getConversationId())
                        .eq(query.getCallStatus() != null, AUsageLog::getCallStatus, query.getCallStatus())
                        .orderByDesc(AUsageLog::getCreatedAt));
        return PageUtils.convert(page, row -> BeanCopyUtils.copy(row, AUsageLogDTO.class));
    }

    private void validate(AUsageLogReq req) {
        BizAssert.isTrue(AgentConstants.CALL_STATUS_SUCCESS == req.getCallStatus()
                        || AgentConstants.CALL_STATUS_FAIL == req.getCallStatus(),
                ErrorCode.PARAM_ERROR, "调用结果只允许 1成功 2失败");
        BizAssert.isTrue(req.getErrMsg() == null || req.getErrMsg().length() <= ERR_MSG_MAX_LEN,
                ErrorCode.PARAM_ERROR, "失败原因长度不能超过 512");
        BizAssert.isTrue(req.getInputTokens() == null || req.getInputTokens() >= 0,
                ErrorCode.PARAM_ERROR, "新增输入 token 不能为负");
        BizAssert.isTrue(req.getCachedTokens() == null || req.getCachedTokens() >= 0,
                ErrorCode.PARAM_ERROR, "缓存命中 token 不能为负");
        BizAssert.isTrue(req.getOutputTokens() == null || req.getOutputTokens() >= 0,
                ErrorCode.PARAM_ERROR, "输出 token 不能为负");
        BizAssert.isTrue(req.getLatencyMs() == null || req.getLatencyMs() >= 0,
                ErrorCode.PARAM_ERROR, "耗时不能为负");
    }

    /**
     * 统计当天成功使用的次数
     */
    public Integer countWithSuccessAndToday(Long userId) {
        return Math.toIntExact(usageLogMapper.selectCount(Wrappers.<AUsageLog>lambdaQuery()
                .eq(userId != null, AUsageLog::getUserId, userId)
                .eq(AUsageLog::getCallStatus, AgentConstants.CALL_STATUS_SUCCESS)
                .ge(AUsageLog::getCreatedAt, LocalDate.now().atStartOfDay())));
    }
}
