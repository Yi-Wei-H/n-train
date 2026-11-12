package com.weiyi.agent.server.dto;

import lombok.Data;

/**
 * AI 预订单确认卡片数据（conversations/current 出参与 SSE confirmation 事件共用）：
 * 只含展示字段——操作 id / confirmId 不出服务端，确认动作由前端只发意图、服务端按身份反查。
 */
@Data
public class PendingConfirmVO {

    /** 操作类型：1 购票 / 2 退票（AgentConstants.OPERATION_TYPE_*，前端据此定卡片标题） */
    private Integer type;

    /** 确认摘要（业务语言：车次/站对/席别/张数/金额或订单明细，卡片正文） */
    private String summary;

    /** 过期时间文本（WorkMemory 镜像口径，卡片失效提示用；权威判定在服务端） */
    private String expiresAt;
}
