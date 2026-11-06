package com.weiyi.common.redis.service;

/**
 * 逻辑过期缓存值：expireAt 为逻辑过期时间戳（毫秒），物理过期由 Redis key TTL 承担。
 * 逻辑已过但物理未过时读侧拿到旧值并触发异步重建——热 key 永不因回源阻塞。
 */
public record LogicalCacheValue<T>(T data, long expireAt) {
}
