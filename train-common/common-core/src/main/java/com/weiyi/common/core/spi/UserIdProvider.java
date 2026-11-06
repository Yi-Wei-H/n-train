package com.weiyi.common.core.spi;

/**
 * 限流切面的“当前用户”SPI：common-redis 消费、上游（common-security）提供，
 * 避免 sibling 模块互相依赖。
 */
public interface UserIdProvider {

    /**
     * @return 当前登录用户 ID；未登录返回 null（切面会回退为匿名维度）
     */
    Long getUserId();
}
