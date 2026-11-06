package com.weiyi.common.core.constant;

/**
 * 订单域状态常量（与 DDL 注释一致，DB 存 TINYINT）。
 */
public final class StatusConsts {

    private StatusConsts() {
    }

    /** b_order.order_status */
    public static final int ORDER_WAIT_PAY = 0;
    public static final int ORDER_PAID = 1;          // 已支付(待出行)
    public static final int ORDER_TRAVELED = 2;      // 已出行
    public static final int ORDER_COMPLETED = 3;     // 已完成
    public static final int ORDER_CANCELED = 4;      // 已取消(超时/手动)
    public static final int ORDER_REFUNDING = 5;     // 退款中
    public static final int ORDER_CLOSED = 6;        // 已关闭

    /** b_order.cancel_type */
    public static final int CANCEL_TIMEOUT = 1;
    public static final int CANCEL_MANUAL = 2;

    /** b_order_item.item_status */
    public static final int ITEM_WAIT_PAY = 0;
    public static final int ITEM_PAID = 1;
    public static final int ITEM_TRAVELED = 2;
    public static final int ITEM_REFUNDING = 3;
    public static final int ITEM_REFUNDED = 4;

    /** m_ticket.ticket_status */
    public static final int TICKET_WAIT_RIDE = 1;
    public static final int TICKET_TRAVELED = 2;
    public static final int TICKET_REFUNDED = 3;
    public static final int TICKET_INVALID = 4;

    /** m_wallet_flow.flow_type */
    public static final int FLOW_RECHARGE = 1;
    public static final int FLOW_PAY = 2;
    public static final int FLOW_REFUND = 3;

    /** 席别 */
    public static final int SEAT_BUSINESS = 1;
    public static final int SEAT_FIRST = 2;
    public static final int SEAT_SECOND = 3;
    public static final int SEAT_HARD_SLEEPER = 4;
    public static final int SEAT_HARD = 5;

    /** 车次类型 */
    public static final int TRAIN_GAOTIE = 1;
    public static final int TRAIN_DONGCHE = 2;
    public static final int TRAIN_PUAI = 3;
    public static final int TRAIN_ZHIDA = 4;

    /** b_payment.pay_status */
    public static final int PAY_PROCESSING = 0;
    public static final int PAY_SUCCESS = 1;
    public static final int PAY_FAIL = 2;

    /** b_daily_train.status（发车状态巡检推进） */
    public static final int TRAIN_STATUS_NOT_DEPARTED = 0;  // 未发车
    public static final int TRAIN_STATUS_RUNNING = 1;       // 运行中
    public static final int TRAIN_STATUS_ARRIVED = 2;       // 已到达
}
