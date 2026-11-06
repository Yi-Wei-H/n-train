package com.weiyi.admin.shiro;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Shiro 免鉴权路径（登录前可访问的接口；兜底默认值防漏配把全部接口锁死） */
@Component
@ConfigurationProperties(prefix = "ntrain.shiro")
public class ShiroAnonProperties {

    private List<String> anonPaths = new ArrayList<>(List.of(
            "/auth/login",
            "/auth/public-key",
            "/v3/api-docs/**"));

    public List<String> getAnonPaths() {
        return anonPaths;
    }

    public void setAnonPaths(List<String> anonPaths) {
        this.anonPaths = anonPaths;
    }
}
