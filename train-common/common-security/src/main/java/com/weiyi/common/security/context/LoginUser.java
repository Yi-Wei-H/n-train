package com.weiyi.common.security.context;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 当前登录用户（由网关校验 token 后写入请求头，下游服务解析请求头得到）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {

    private Long userId;
    /** Constants.USER_TYPE_MEMBER / USER_TYPE_ADMIN */
    private String userType;
    private String username;
    /** 超级管理员（仅管理端登录态携带，task 服务的管理台接口强校验用） */
    private Boolean superAdmin;
}
