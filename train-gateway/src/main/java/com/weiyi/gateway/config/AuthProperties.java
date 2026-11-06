package com.weiyi.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关鉴权配置绑定。
 * 注意：YAML 列表不存在平铺 key，@Value("${ntrain.auth.whitelist}") 解析不到，
 * 必须用 @ConfigurationProperties 绑定
 */
@Component
@ConfigurationProperties(prefix = "ntrain.auth")
public class AuthProperties {

    /** 免登录白名单 */
    private List<String> whitelist = new ArrayList<>();

    /** 可信代理地址：仅当网关直连对端在列表内时才采信其追加的 X-Forwarded-For（取末段）；缺省空=一律按连接地址 */
    private List<String> trustedProxies = new ArrayList<>();

    public List<String> getWhitelist() {
        return whitelist;
    }

    public void setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist;
    }

    public List<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies;
    }
}
