package com.weiyi.member.api.dto;

import lombok.Data;

import java.io.Serializable;

/** 会员登录态（仅 data 端内部返回，含密文） */
@Data
public class MemberAuthDTO implements Serializable {

    private Long id;
    private String phone;
    /** BCrypt 密文 */
    private String password;
    private String nickname;
    private Integer status;
}
