-- 滑动窗口限流（ZSET 实现，原子执行）
-- KEYS[1] 窗口 key；ARGV[1] 当前毫秒；ARGV[2] 窗口毫秒；ARGV[3] 限流次数
-- 返回 1=放行 0=拒绝
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])

redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
local current = redis.call('ZCARD', key)
if current < limit then
    -- member 需唯一：毫秒 + 随机数
    redis.call('ZADD', key, now, now .. '-' .. math.random(1, 1000000))
    redis.call('PEXPIRE', key, window)
    return 1
end
return 0
