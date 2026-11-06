package com.weiyi.common.mq.core;

import java.time.Duration;

/**
 * MQ 主题/标签/消费组/延迟级别统一管理。
 * 约定：Topic 按业务域聚合，Tag 区分事件；消费组按"服务 × 主题"命名，
 * 同组内扩容广播会重复消费，必须靠业务侧幂等（状态条件更新）兜底。
 * 消费失败默认重试 16 次后进死信 %DLQ%{group}，需人工处理。
 */
public final class MqConst {

    // ==================== Topic ====================

    /** 订单生命周期事件（事务消息）：关单/退款完成 → 会员域失效余票缓存（座位释放已在 business 本地事务完成） */
    public static final String TOPIC_ORDER_EVENT = "T_ORDER_EVENT";

    /** 订单超时检查（延迟消息）：到期后回查支付状态，未支付则关单 */
    public static final String TOPIC_ORDER_TIMEOUT = "T_ORDER_TIMEOUT";

    /** 跨服务日志事件：会员端登录日志 → 管理端落库 */
    public static final String TOPIC_LOG_EVENT = "T_LOG_EVENT";

    // ==================== Tag ====================

    /** 订单关闭（超时/手动），携带 orderNo 供会员域失效余票缓存 */
    public static final String TAG_ORDER_CLOSED = "ORDER_CLOSED";

    /** 退款批次确认完成，携带明细 ID 列表 */
    public static final String TAG_ORDER_REFUNDED = "ORDER_REFUNDED";

    /** 会员登录日志 */
    public static final String TAG_LOG_LOGIN = "LOG_LOGIN";

    /** 管理端操作日志（train-admin 切面采集发送，admin 消费落 sys_oper_log） */
    public static final String TAG_LOG_OPER = "LOG_OPER";

    // ==================== 消费组 ====================

    /** train-member 消费订单事件（余票缓存失效） */
    public static final String GROUP_MEMBER_ORDER = "G_MEMBER_ORDER_EVENT";

    /** train-business 自身的超时关单消费组 */
    public static final String GROUP_ORDER_TIMEOUT = "G_ORDER_TIMEOUT";

    /** train-admin 消费跨服务日志 */
    public static final String GROUP_ADMIN_LOG = "G_ADMIN_LOG";

    // ==================== 延迟级别 ====================
    // RocketMQ 4.x 协议为 18 个固定档位：1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
    // 业务时长没有精确档位时（如 15 分钟支付时限）用 delayLevelCovering 取覆盖档位，
    // 消费端按业务截止时间复判，未到期则 re-arm 补投，不依赖档位对齐。

    public static final int DELAY_1_SECOND = 1;
    public static final int DELAY_10_SECOND = 3;
    public static final int DELAY_1_MINUTE = 5;
    public static final int DELAY_10_MINUTE = 14;
    public static final int DELAY_20_MINUTE = 15;
    public static final int DELAY_30_MINUTE = 16;
    public static final int DELAY_1_HOUR = 17;
    public static final int DELAY_2_HOUR = 18;

    /** 各档位实际时长，下标 = 档位号（0 位不用） */
    private static final Duration[] LEVEL_DURATIONS = {
            null,
            Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30),
            Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(3), Duration.ofMinutes(4),
            Duration.ofMinutes(5), Duration.ofMinutes(6), Duration.ofMinutes(7), Duration.ofMinutes(8),
            Duration.ofMinutes(9), Duration.ofMinutes(10), Duration.ofMinutes(20), Duration.ofMinutes(30),
            Duration.ofHours(1), Duration.ofHours(2)
    };

    /**
     * 取能完整覆盖所需时长的最小延迟档位（如 15 分钟 → 20 分钟档）。
     * 超过最大档位（2h）时封顶返回 18，由消费端 re-arm 逻辑继续接力。
     */
    public static int delayLevelCovering(Duration required) {
        for (int level = 1; level < LEVEL_DURATIONS.length; level++) {
            if (!LEVEL_DURATIONS[level].minus(required).isNegative()) {
                return level;
            }
        }
        return DELAY_2_HOUR;
    }

    private MqConst() {
    }
}

