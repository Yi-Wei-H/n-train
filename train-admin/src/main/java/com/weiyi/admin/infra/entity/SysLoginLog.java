package com.weiyi.admin.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录日志（仅插入与查询，无更新时间/逻辑删除）。
 */
@Data
@TableName("sys_login_log")
public class SysLoginLog implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** U 用户 / A 管理员 */
    private String userType;
    private Long userId;
    private String username;
    private String ip;
    private String userAgent;
    private Integer status;
    private String msg;
    private LocalDateTime loginAt;
}
