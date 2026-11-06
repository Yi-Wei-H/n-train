package com.weiyi.agent.server.req;

import lombok.Data;

/**
 * 聊天请求入参。身份不随请求体传递：由网关注入 X-User-* 头、服务端经 LoginContext 还原，
 * 请求体内的任何身份字段都会被忽略；会话由服务端按账户指针定位，不接受客户端指定。
 */
@Data
public class ChatReq {

    /** 文本消息（文本轮必填；卡片轮可作为补充内容并入 userContext 槽位） */
    private String content;

    /** 便捷卡片 ID：卡片轮提交，点击即发送 */
    private Long cardId;

    /** 卡片轮用户补充内容；未来拓展 */
    private String userContext;

    /** 客户端请求唯一标识（幂等锚点）：必填，任何重试必须换新值 */
    private String clientRequestId;
}
