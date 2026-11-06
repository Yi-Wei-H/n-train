package com.weiyi.task.dto;

import lombok.Data;

import java.io.Serializable;

/** 定时任务列表项（含状态、cron、上次/下次执行时间、中文说明） */
@Data
public class CronJobResp implements Serializable {

    private String name;

    private String group;

    /** 白名单中文名（如"每日车次生成"） */
    private String cnName;

    private String description;

    /** NORMAL/PAUSED/ERROR/BLOCKED... */
    private String state;

    private String cronExpression;

    private String cronDesc;

    private String nextFireTime;

    private String preFireTime;
}
