package com.weiyi.business.order.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款批次：申请（0 处理中）→ 钱包入账（会员域）→ 确认（1 完成，事务消息广播释放座位）。
 * batchNo 是三段流程的公共幂等键：钱包流水 biz_no 与确认重试都靠它去重。
 */
@Data
@TableName("b_refund_batch")
public class BRefundBatch implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String batchNo;

    private Long orderId;

    private Long memberId;

    private BigDecimal refundAmount;

    /** 0 处理中（已申请，等钱包入账后确认） 1 已确认（明细已退、座位已释放） */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime confirmedAt;
}
