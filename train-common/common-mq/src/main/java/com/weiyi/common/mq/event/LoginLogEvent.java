package com.weiyi.common.mq.event;

import lombok.Data;

import java.io.Serializable;

/** 会员端登录日志事件：member 发出，admin 消费落 sys_login_log（fire-and-forget，允许少量丢失） */
@Data
public class LoginLogEvent implements Serializable {

    private Long userId;

    private String username;

    private String ip;

    private String userAgent;

    /** 1 成功 0 失败 */
    private Integer status;

    private String msg;
}
