package com.weiyi.task.dto;

import lombok.Data;

import java.io.Serializable;

/** 可选任务白名单项（新增弹窗下拉） */
@Data
public class JobClassResp implements Serializable {

    private String name;

    private String code;

    private String desc;

    private String description;

    /** 建议默认 cron（新增时前端自动带出） */
    private String defaultCron;
}
