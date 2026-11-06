package com.weiyi.agent.server.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * AI 写操作（购票/退票预检-确认-执行的状态机行，DDL 真相源 sql/agent/01_schema.sql）。
 * 预检创建 PENDING；确认轮 CAS 消费转 EXECUTING；终态 SUCCESS/FAILED/UNKNOWN/CANCELLED/EXPIRED。
 * 该表是确认有效性的权威，Redis 待确认单仅为镜像。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("a_ai_operation")
public class AOperation extends BaseTimeEntity {

    /** 用户 id */
    private Long userId;

    /** 所属会话 */
    private Long conversationId;

    /** 1 购票 2 退票 */
    private Integer type;

    /** 确认摘要引用（不可猜测但非密钥，明文保存支持刷新/换设备恢复） */
    private String confirmId;

    /** 服务端模板渲染的确认摘要文本 */
    private String summaryText;

    /** 摘要业务快照 JSON（执行复检与幂等复核的比对基准） */
    private String summarySnapshot;

    /** 摘要版本（防旧 confirmId 确认新摘要） */
    private Integer summaryVersion;

    /** 业务幂等键（= operationId，member 写接口按此去重），唯一约束 */
    private String idempotencyKey;

    /** 状态：0 待确认 1 执行中 2 成功 3 失败 4 结果不明 5 已取消 6 已过期 */
    private Integer status;

    /** 业务单号（订单号/退款批次号） */
    private String bizNo;

    /** 失败/取消原因码 */
    private String errorCode;

    /** 确认令牌消费时间（CAS 成功即写入） */
    private LocalDateTime consumedAt;

    /** 确认摘要失效时间 */
    private LocalDateTime expiresAt;

    private LocalDateTime executionStartedAt;

    /** 执行租约到期时间（执行实例心跳续期，崩溃后恢复任务据此回收） */
    private LocalDateTime executionLeaseUntil;

    /** 恢复任务已尝试次数 */
    private Integer recoveryAttempts;
}
