package com.weiyi.task.dto;

import lombok.Data;

import java.io.Serializable;

/** 定时任务管理命令（新增/编辑/暂停/恢复/手动执行/删除共用，name 须在 JobEnum 白名单内） */
@Data
public class CronJobReq implements Serializable {

    private String group;

    private String name;

    private String description;

    private String cronExpression;
}
