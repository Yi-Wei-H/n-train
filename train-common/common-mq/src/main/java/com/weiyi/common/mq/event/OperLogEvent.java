package com.weiyi.common.mq.event;

import lombok.Data;

import java.io.Serializable;

/** 操作日志事件（管理端写操作，admin 切面采集经 MQ 异步落 sys_oper_log） */
@Data
public class OperLogEvent implements Serializable {

    private Long userId;

    private String username;

    private String module;

    private String operation;

    private String method;

    private String params;

    private Integer resultCode;

    private String ip;

    private Integer costMs;
}
