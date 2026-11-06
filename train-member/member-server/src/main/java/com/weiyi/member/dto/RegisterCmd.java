package com.weiyi.member.dto;

/** 会员注册服务命令（由 RegisterWebCmd 转换，密码为 RSA 密文，服务端解密后落库） */
public record RegisterCmd(String phone, String idCardNo, String keyId, String cipher,
                          String captchaKey, String captchaCode) {
}
