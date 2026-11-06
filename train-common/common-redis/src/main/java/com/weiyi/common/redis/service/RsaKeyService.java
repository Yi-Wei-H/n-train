package com.weiyi.common.redis.service;

import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.redis.constant.RedisKeys;
import com.weiyi.common.redis.dto.PublicKeyVO;
import lombok.RequiredArgsConstructor;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * RSA 短时效密钥服务：登录/注册前下发公钥，密码密文提交；
 * 私钥存 Redis 2 分钟，取出即删（一次性），防止密文重放。
 * 前端用 jsencrypt 加密（仅支持 PKCS#1 v1.5 填充）。
 */
@RequiredArgsConstructor
public class RsaKeyService {

    private static final int KEY_SIZE = 2048;
    private static final Duration TTL = Duration.ofMinutes(2);
    private static final String CIPHER_ALGORITHM = "RSA/ECB/PKCS1Padding";

    private final RedisService redisService;

    public PublicKeyVO createKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(KEY_SIZE);
            KeyPair pair = generator.generateKeyPair();
            String keyId = UUID.randomUUID().toString().replace("-", "");
            redisService.set(RedisKeys.rsaKey(keyId),
                    Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()), TTL); // PKCS#8
            String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()); // X.509
            return new PublicKeyVO(keyId, publicKey);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成加密密钥对失败");
        }
    }

    /** 取出并删除私钥后解密；凭证过期/不存在/解密失败统一抛 RSA_KEY_INVALID，不区分细节防探测 */
    public String decrypt(String keyId, String cipherBase64) {
        String privateKeyBase64 = redisService.getAndDelete(RedisKeys.rsaKey(keyId), String.class);
        if (privateKeyBase64 == null || cipherBase64 == null || cipherBase64.isBlank()) {
            throw new BusinessException(ErrorCode.RSA_KEY_INVALID);
        }
        try {
            PrivateKey privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyBase64)));
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return new String(cipher.doFinal(Base64.getDecoder().decode(cipherBase64)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.RSA_KEY_INVALID);
        }
    }
}
