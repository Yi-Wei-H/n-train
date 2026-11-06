package com.weiyi.common.redis.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.weiyi.common.redis.config.CodecMappers;
import com.weiyi.common.redis.constant.RedisKeys;
import com.weiyi.common.redis.ratelimit.SlidingWindowRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.codec.TypedJsonJacksonCodec;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redisson 通用封装：缓存读写（显式类型，规避 JSON 反序列化成 Map）、
 * 查询为null时，设置null（防穿透）
 * getOrLoad（带 TTL 抖动防雪崩）、分布式锁。
 */
@Slf4j
@RequiredArgsConstructor
public class RedisService {

    private final RedissonClient redissonClient;
    private final SlidingWindowRateLimiter slidingWindowRateLimiter;

    /** 逻辑过期缓存的异步重建线程池（daemon，IO 型小池即可：单飞锁已保证每 key 至多一个任务） */
    private final ExecutorService rebuildExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "ntrain-cache-rebuild");
        t.setDaemon(true);
        return t;
    });

    public <T> void set(String key, T value, Duration ttl) {
        redissonClient.getBucket(key, new TypedJsonJacksonCodec(Object.class, CodecMappers.CODEC))
                .set(value, ttl);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        RBucket<Object> bucket = redissonClient.getBucket(key, new TypedJsonJacksonCodec(type, CodecMappers.CODEC));
        return (T) bucket.get();
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, TypeReference<T> type) {
        RBucket<Object> bucket = redissonClient.getBucket(key, new TypedJsonJacksonCodec(type, CodecMappers.CODEC));
        return (T) bucket.get();
    }

    public boolean hasKey(String key) {
        return redissonClient.getBucket(key).isExists();
    }

    /** 读取并原子删除（一次性消费场景，如 RSA 私钥取出即焚） */
    @SuppressWarnings("unchecked")
    public <T> T getAndDelete(String key, Class<T> type) {
        RBucket<Object> bucket = redissonClient.getBucket(key, new TypedJsonJacksonCodec(type, CodecMappers.CODEC));
        return (T) bucket.getAndDelete();
    }

    /** 原子计数 +1，首次计数时设置 TTL（失败计数/锁定窗口场景） */
    public long increment(String key, Duration ttl) {
        RAtomicLong counter = redissonClient.getAtomicLong(key);
        long value = counter.incrementAndGet();
        if (value == 1) {
            counter.expire(ttl);
        }
        return value;
    }

    /** 读计数当前值（与 increment 配对，RAtomicLong 非 JSON 编码，不能用 get 读） */
    public long getCounter(String key) {
        return redissonClient.getAtomicLong(key).get();
    }

    public void delete(String key) {
        redissonClient.getBucket(key).delete();
    }

    /** 按模式批量删除（如角色/菜单变更后清空全部管理员权限缓存） */
    public void deleteByPattern(String pattern) {
        redissonClient.getKeys().deleteByPattern(pattern);
    }

    /** 仅当 key 不存在时写入（SET NX PX 语义）：跨线程互斥占位/防重复提交，返回是否抢占成功 */
    public boolean setIfAbsent(String key, String value, long ttlMillis) {
        return redissonClient.getBucket(key).setIfAbsent(value, Duration.ofMillis(ttlMillis));
    }

    /**
     * 缓存优先读取，未命中执行 loader 并回填；TTL 附加 0~10% 随机抖动。
     * loader 返回 null 时缓存空值（短 TTL）防穿透。
     */
    public <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        T cached = get(key, type);
        if (cached != null) {
            return cached;
        }
        T loaded = loader.get();
        if (loaded == null) {
            set(key, null, Duration.ofSeconds(30));
            return null;
        }
        set(key, loaded, jitter(ttl));
        return loaded;
    }

    public <T> T getOrLoad(String key, TypeReference<T> type, Duration ttl, Supplier<T> loader) {
        T cached = get(key, type);
        if (cached != null) {
            return cached;
        }
        T loaded = loader.get();
        if (loaded == null) {
            set(key, null, Duration.ofSeconds(30));
            return null;
        }
        set(key, loaded, jitter(ttl));
        return loaded;
    }

    /**
     * 逻辑过期缓存读取（防击穿）：
     * <ul>
     *   <li>命中且逻辑未过期 → 直接返回 data（常态路径，一次 Redis 读）</li>
     *   <li>命中但逻辑过期 → <b>立即返回旧值</b>，抢到锁（tryLock 0）的请求单飞异步重建，
     *       抢不到说明已有人在重建——热 key 永不因回源阻塞</li>
     *   <li>未命中（冷 key / 物理过期）→ 加锁同步回源（等待 2s；锁被占则轮询等他人回填）防冷启动击穿</li>
     * </ul>
     * loader 返回 null 时缓存空 wrapper（短 TTL）防穿透，与 getOrLoad 语义一致。
     */
    public <T> T getOrLoadLogical(String key, TypeReference<LogicalCacheValue<T>> type,
                                  Duration logicalTtl, Duration physicalTtl, Supplier<T> loader) {
        // ① 命中且逻辑未过期
        LogicalCacheValue<T> cached = get(key, type);
        if (cached != null && cached.expireAt() > System.currentTimeMillis()) {
            return cached.data();
        }
        String lock = RedisKeys.cacheRebuildLock(key);
        if (cached != null) {
            // ② 逻辑过期：旧值秒回 + 单飞异步重建
            if (tryLock(lock, 0, Math.max(physicalTtl.toMillis(), 10_000))) {
                try {
                    rebuildExecutor.execute(() -> {
                        try {
                            setWrapperValue(key, loader.get(), logicalTtl, physicalTtl);
                        } catch (Exception e) {
                            log.warn("缓存异步重建失败 {}: {}", key, e.getMessage());
                        } finally {
                            unlock(lock);
                        }
                    });
                } catch (RuntimeException e) {
                    unlock(lock); // 线程池饱和/关闭拒绝任务时立即放锁
                }
            }
            return cached.data();
        }
        // ③ 冷 key：同步加锁回源（锁内双重检查，等锁期间可能已被他人回填）
        if (tryLock(lock, 2000, 10_000)) {
            try {
                LogicalCacheValue<T> again = get(key, type);
                if (again != null) {
                    return again.data();
                }
                T loaded = loader.get();
                setWrapperValue(key, loaded, logicalTtl, physicalTtl);
                return loaded;
            } finally {
                unlock(lock);
            }
        }
        for (int i = 0; i < 20; i++) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            LogicalCacheValue<T> again = get(key, type);
            if (again != null) {
                return again.data();
            }
        }
        // 轮询超时兜底：宁可多回源一次，不向调用方报错
        T loaded = loader.get();
        setWrapperValue(key, loaded, logicalTtl, physicalTtl);
        return loaded;
    }

    private <T> void setWrapperValue(String key, T loaded, Duration logicalTtl, Duration physicalTtl) {
        if (loaded == null) {
            set(key, new LogicalCacheValue<>(null, System.currentTimeMillis() + 30_000),
                    Duration.ofSeconds(60));
            return;
        }
        set(key, new LogicalCacheValue<>(loaded, System.currentTimeMillis() + logicalTtl.toMillis()),
                physicalTtl);
    }

    /** 非公平可重入锁：等待 waitMs，拿到后 leaseMs 自动释放（防宕机死锁） */
    public boolean tryLock(String name, long waitMs, long leaseMs) {
        try {
            return redissonClient.getLock(name).tryLock(waitMs, leaseMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void unlock(String name) {
        RLock lock = redissonClient.getLock(name);
        if (lock.isLocked() && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /**
     * 滑动窗口计数（只计数不拒绝）：记一次当前时间，清掉窗外记录，返回窗口内次数。
     * 用于"下单频次触发验证码"这类观察型场景。
     */
    public Boolean slidingWindow(String key, long windowMs, int windowCount) {
        return slidingWindowRateLimiter.useWindowRateLimiter(redissonClient, key, System.currentTimeMillis(), windowMs, windowCount);
    }

    private Duration jitter(Duration ttl) {
        long base = ttl.toMillis();
        return Duration.ofMillis(base + ThreadLocalRandom.current().nextLong(base / 10 + 1));
    }
}
