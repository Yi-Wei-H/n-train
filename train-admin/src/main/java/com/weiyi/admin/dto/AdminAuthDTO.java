package com.weiyi.admin.dto;

import lombok.Data;

import java.io.Serializable;

/** 管理员登录态（仅 data 端内部返回，含密文；密码绝不外传到响应） */
@Data
public class AdminAuthDTO implements Serializable {

    private Long id;
    private String username;
    /** BCrypt 密文 */
    private String password;
    private String nickname;
    /** S 超管 / A 普管 */
    private String userType;
    private Integer status;
    private Long deptId;
}
