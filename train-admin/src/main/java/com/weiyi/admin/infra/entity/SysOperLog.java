package com.weiyi.admin.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志（仅插入与查询）。
 */
@Data
@TableName("sys_oper_log")
public class SysOperLog implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private String username;
    private String module;
    private String operation;
    private String method;
    private String params;
    private Integer resultCode;
    private String ip;
    private Integer costMs;
    private LocalDateTime operAt;
}
