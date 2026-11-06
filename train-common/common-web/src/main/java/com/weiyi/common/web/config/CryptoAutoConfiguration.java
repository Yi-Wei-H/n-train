package com.weiyi.common.web.config;

import com.weiyi.common.core.util.AesUtil;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 敏感字段 AES 密钥装配：从配置读取 ntrain.crypto.aes-key 注入 AesUtil。
 * 与前端 AES 密钥保持一致。
 */
@AutoConfiguration
public class CryptoAutoConfiguration {

    @Bean
    public InitializingBean aesKeyInitializer(@Value("${ntrain.crypto.aes-key}") String aesKey) {
        return () -> AesUtil.init(aesKey);
    }
}
