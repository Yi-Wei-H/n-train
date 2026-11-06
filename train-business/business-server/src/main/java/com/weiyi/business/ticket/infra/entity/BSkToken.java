package com.weiyi.business.ticket.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 令牌大闸：按"日期+车次"控制进入下单事务的总请求数。
 * remaining 为权威剩余（= 发放量 − 成功订单数），Redis 计数器丢失时按此懒重建。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("b_sk_token")
public class BSkToken extends BaseTimeEntity {

    private LocalDate rideDate;

    private String trainCode;

    /** 累计已发放令牌数（初始 + 补偿追加，只增） */
    private Integer totalCount;

    /** 当前剩余（成功订单事务内条件扣减） */
    private Integer remaining;
}
