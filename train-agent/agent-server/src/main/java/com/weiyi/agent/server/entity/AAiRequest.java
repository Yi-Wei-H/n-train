package com.weiyi.agent.server.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 请求回执（DDL 真相源 sql/agent/01_schema.sql）：clientRequestId 幂等锚点，
 * user_id + client_request_id 唯一；主模型调用前同事务创建 PROCESSING，
 * 流结束置 SUCCESS（关联 messageId）/ FAILED，忙拒绝落 REJECTED。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("a_ai_request")
public class AAiRequest extends BaseTimeEntity {

    /** 属主用户 id */
    private Long userId;

    /** 客户端请求唯一标识，重试须换新值 */
    private String clientRequestId;

    /** 关联会话 id */
    private Long conversationId;

    /** 状态：1处理中 2成功 3失败 4忙拒绝 */
    private Integer requestStatus;

    /** 成功轮消息 id（SUCCESS 时回填） */
    private Long messageId;

    /** 失败/拒绝原因 */
    private String errMsg;
}
