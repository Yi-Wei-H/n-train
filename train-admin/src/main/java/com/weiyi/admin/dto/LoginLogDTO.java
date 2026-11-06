package com.weiyi.admin.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 登录日志行 */
@Data
public class LoginLogDTO implements Serializable {

    private Long id;
    private String userType;
    private Long userId;
    private String username;
    private String ip;
    private Integer status;
    private String msg;
    private LocalDateTime loginAt;
}
