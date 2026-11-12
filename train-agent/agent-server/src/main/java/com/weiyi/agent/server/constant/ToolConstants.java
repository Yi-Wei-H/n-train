package com.weiyi.agent.server.constant;

import java.util.Map;

/**
 * 工具层常量：引用种类、席别名称、展示脱敏。
 */
public final class ToolConstants {

    /** 确认预支付订单 */
    public static final String CHAT_ORDER_CONFIRM = "CONFIRM";

    /** 取消预支付订单 */
    public static final String CHAT_ORDER_CANCEL = "CANCEL";

    /** 引用种类：车票查询列表（listToken） */
    public static final String REF_KIND_LIST = "LIST";

    /** 引用种类：车票（ticketRef，指向某车次某日期某区间的查询结果项） */
    public static final String REF_KIND_TICKET = "TICKET";

    /** 引用种类：乘车人（passengerRef，映射真实乘车人 id） */
    public static final String REF_KIND_PASSENGER = "PASSENGER";

    /** 引用种类：订单（orderRef，映射真实订单 id） */
    public static final String REF_KIND_ORDER = "ORDER";

    /** 引用种类：订单明细（itemRef，映射订单内某张票） */
    public static final String REF_KIND_ORDER_ITEM = "ORDER_ITEM";

    /** 单轮工具循环上限：超过后编排方终止本轮并提示重试 */
    public static final int TOOL_LOOP_LIMIT = 6;

    /** 工具返回给模型的车票列表最大条数 */
    public static final int TICKET_LIST_LIMIT = 20;

    /** 席别名称（与业务域席别代码一致） */
    private static final Map<Integer, String> SEAT_TYPE_NAMES =
            Map.of(1, "商务座", 2, "一等座", 3, "二等座", 4, "硬卧", 5, "硬座");

    private static final Map<Integer, String> PASSENGER_TYPE_NAMES =
            Map.of(1, "成人", 2, "儿童", 3, "学生");

    private ToolConstants() {
    }

    public static String seatName(Integer seatType) {
        return seatType == null ? null : SEAT_TYPE_NAMES.getOrDefault(seatType, "席别" + seatType);
    }

    public static String passengerName(Integer passengerType) {
        return passengerType == null ? null : PASSENGER_TYPE_NAMES.getOrDefault(passengerType, "类型" + passengerType);
    }

    /** 姓名脱敏：保留首字，其余以 * 掩码（单字姓名整体掩码） */
    public static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return "**";
        }
        if (name.length() == 1) {
            return "*";
        }
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }

    /** 订单号脱敏：保留首尾各 2 位，中间掩码 */
    public static String maskOrderNo(String orderNo) {
        if (orderNo == null || orderNo.length() <= 4) {
            return "****";
        }
        return orderNo.substring(0, 2) + "****" + orderNo.substring(orderNo.length() - 2);
    }
}
