package com.weiyi.admin.dto;

/** 管理员登录命令（密码为 RSA 密文，keyId 对应 RsaKeyService 下发的短时效公钥） */
public record LoginCmd(String username, String keyId, String cipher) {
}
