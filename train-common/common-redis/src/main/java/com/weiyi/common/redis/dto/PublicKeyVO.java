package com.weiyi.common.redis.dto;

/** RSA 公钥下发结果（私钥存 Redis 2 分钟一次性，前端用 jsencrypt 加密密码后回传 keyId + cipher） */
public record PublicKeyVO(String keyId, String publicKey) {
}
