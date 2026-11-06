package com.weiyi.common.core.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局错误码。分段约定：
 * 4xxxxx 客户端/业务错误；42xxx 钱包；43xxx 订单票务；44xxx/45xxx agent 模型配置；5xxxxx 系统错误。
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(0, "成功"),
    ERROR(500, "服务繁忙，请稍后重试"),

    // ---------- 通用 ----------
    PARAM_ERROR(40000, "参数错误"),
    UNAUTHORIZED(40100, "未登录或凭证无效"),
    TOKEN_EXPIRED(40101, "登录已过期，请重新登录"),
    FORBIDDEN(40300, "无权限访问"),
    NOT_FOUND(40400, "资源不存在"),
    RATE_LIMITED(42900, "请求过于频繁，请稍后再试"),
    DATA_CONFLICT(44000, "数据冲突，请重试"),
    REMOTE_ERROR(50001, "下游服务调用失败"),
    SYSTEM_ERROR(50000, "系统繁忙，请稍后再试"),

    // ---------- 账号 ----------
    PHONE_EXISTS(41001, "手机号已注册"),
    ACCOUNT_NOT_EXISTS(41002, "账号不存在"),
    PASSWORD_ERROR(41003, "账号或密码错误"),
    ACCOUNT_DISABLED(41004, "账号已被禁用"),
    CAPTCHA_ERROR(41005, "验证码错误或已过期"),
    CAPTCHA_REQUIRED(41006, "操作频繁，请先完成图形验证"),
    OLD_PASSWORD_ERROR(41007, "原密码错误"),
    RSA_KEY_INVALID(41010, "加密凭证无效或已过期，请刷新重试"),
    ACCOUNT_LOCKED(41011, "密码错误次数过多，账号已锁定，请稍后再试"),
    SMS_CODE_INVALID(41012, "短信验证码错误或已过期"),
    SMS_SEND_FREQUENT(41013, "验证码发送过于频繁，请稍后再试"),
    ID_CARD_EXISTS(41014, "该身份证已注册"),
    PASSENGER_SELF_PROTECTED(41015, "本人乘车人不可删除"),

    // ---------- 乘车人 ----------
    PASSENGER_EXISTS(41008, "该乘车人已存在"),
    PASSENGER_NOT_EXISTS(41009, "乘车人不存在"),

    // ---------- 钱包 ----------
    WALLET_NOT_EXISTS(42001, "钱包不存在"),
    WALLET_NOT_ENOUGH(42002, "余额不足，请先充值"),
    WALLET_FROZEN(42003, "钱包已被冻结"),
    RECHARGE_OVER_LIMIT(42004, "超出钱包余额上限"),

    // ---------- 订单/票务 ----------
    ORDER_NOT_EXISTS(43001, "订单不存在"),
    ORDER_STATUS_ERROR(43002, "订单状态不允许该操作"),
    TICKET_NOT_ENOUGH(43003, "余票不足，请更换车次或席别"),
    TRAIN_NOT_EXISTS(43004, "车次不存在或未开售"),
    SEAT_ALLOC_FAIL(43005, "座位分配失败，请重试"),
    ITEM_NOT_REFUNDABLE(43006, "存在不可退票的订单明细"),
    RIDE_DATE_INVALID(43007, "乘车日期不在可售范围内"),
    SK_TOKEN_EXHAUSTED(43008, "当前抢票人数过多，请稍后再试"),

    // ---------- agent：模型预设 / 模型配置 ----------
    MODEL_PRESET_NOT_EXISTS(44001, "模型预设不存在"),

    MODEL_NOT_EXISTS(45001, "模型配置不存在"),
    VECTOR_MODEL_ENABLED_EXISTS(45002, "已存在启用中的向量模型，请先停用再接入"),
    MODEL_NOT_PROVIDE(45003, "该模型暂不支持"),
    MODEL_TYPE_ERROR(45004, "模型类型错误"),
    API_FORMAT_OPENAI_CHAT(45005, "暂不支持该上游格式"),

    QUICK_CARD_NOT_EXISTS(46001, "卡片不存在"),
    QUICK_CARD_NOT_ENABLE(46002, "卡片不能使用"),

    MODEL_USAGE_LIMIT(47001,"今日模型用量以达到上限"),
    NO_SUPPORT_AI_CHAT(47002,"暂不支持使用 AI 交流功能"),
    AI_CONTENT_REJECTED(47003, "该内容暂无法处理，请调整后重试"),
    AI_INPUT_INVALID(47004, "输入内容不合法"),
    AI_REPLY_IN_PROGRESS(47005, "上一条回复进行中，请稍候或刷新查看"),
    AI_REQUEST_FINISHED(47006, "该请求已处理结束，请刷新查看或重新发送");

    private final int code;
    private final String message;
}
