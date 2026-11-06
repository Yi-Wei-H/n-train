package com.weiyi.member.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 写操作幂等结果查询（服务间 RPC 契约）：按幂等键返回业务侧最终结果，供崩溃恢复对账。
 */
@Data
public class MemberOperationResultDTO implements Serializable {

    /** SUCCESS / FAILED / NOT_PROCESSED / UNKNOWN */
    private String status;

    /** 业务单号（订单号 / 退款批次号，可空） */
    private String bizNo;

    /** 失败原因码（FAILED 时给值，可空） */
    private String errorCode;

    private String message;
}
