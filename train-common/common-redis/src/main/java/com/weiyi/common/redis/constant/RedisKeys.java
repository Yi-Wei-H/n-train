package com.weiyi.common.redis.constant;

/**
 * Redis Key 统一管理：一律带 ntrain: 前缀，避免与其它应用混库时冲突。
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 限流滑动窗口：ntrain:rate:{name}:{dim}:{value} */
    public static String rateLimit(String name, String dim, String value) {
        return "ntrain:rate:" + name + ":" + dim + ":" + value;
    }

    /** 图形验证码答案 */
    public static String captcha(String captchaKey) {
        return "ntrain:captcha:" + captchaKey;
    }

    /** 短信验证码（登录用，5 分钟有效，验证即消费） */
    public static String smsCode(String phone) {
        return "ntrain:sms:code:" + phone;
    }

    /** 短信验证码发送冷却（60 秒一次） */
    public static String smsCooldown(String phone) {
        return "ntrain:sms:cooldown:" + phone;
    }

    /** RSA 私钥（短时效、取出即删防重放） */
    public static String rsaKey(String keyId) {
        return "ntrain:rsa:key:" + keyId;
    }

    /** 登录连续失败计数（超限锁定）：按用户类型区分账号空间 */
    public static String loginFailCount(String userType, String username) {
        return "ntrain:login:fail:" + userType + ":" + username;
    }

    /** 下单需验证码标记（10 分钟内有效） */
    public static String orderCaptchaRequired(long memberId) {
        return "ntrain:order:captcha:required:" + memberId;
    }

    /** 下单频次滑动窗口 */
    public static String orderFreq(long memberId) {
        return "ntrain:order:freq:" + memberId;
    }

    /** 余票查询缓存 */
    public static String ticketCache(String rideDate, String fromStation, String toStation) {
        return "ntrain:cache:ticket:" + rideDate + ":" + fromStation + ":" + toStation;
    }

    /** 车站列表缓存 */
    public static final String STATION_CACHE = "ntrain:cache:stations";

    /** 车站编码→站名映射缓存（订单域展示用，1 小时） */
    public static final String STATION_NAME_MAP = "ntrain:cache:station:names";

    /** 车次线路缓存（Route 为 b_train/b_train_station/b_station 派生结果，时刻表调整后删除懒重建） */
    public static String routeCache(String trainCode) {
        return "ntrain:cache:route:" + trainCode;
    }

    /** 管理员权限缓存 */
    public static String adminPerms(long adminId) {
        return "ntrain:admin:perms:" + adminId;
    }

    public static String adminRoles(long adminId) {
        return "ntrain:admin:roles:" + adminId;
    }

    public static String adminMenus(long adminId) {
        return "ntrain:admin:menus:" + adminId;
    }

    /** 管理员是否超管（'1'/'0'） */
    public static String adminSuper(long adminId) {
        return "ntrain:admin:super:" + adminId;
    }

    /** 管理员权限缓存统一前缀（失效清理用） */
    public static final String ADMIN_CACHE_PATTERN = "ntrain:admin:*";

    /** 下单分布式锁（数据端持有）：按日期+车次串行 */
    public static String orderLock(String rideDate, String trainCode) {
        return "ntrain:lock:order:" + rideDate + ":" + trainCode;
    }

    /** 每日车次生成互斥占位（线程无关：请求线程抢占、后台线程收尾，同步/异步路径共用；TTL 兜底防进程崩溃后死占） */
    public static String dailyGenRunning(String rideDate) {
        return "ntrain:lock:dailygen:" + rideDate;
    }

    /** 令牌大闸准入计数器：按日期+车次（DB b_sk_token.remaining 为懒重建来源） */
    public static String skToken(String rideDate, String trainCode) {
        return "ntrain:sk:token:" + rideDate + ":" + trainCode;
    }

    /** 逻辑过期缓存的单飞重建锁（name 为缓存 key 原值） */
    public static String cacheRebuildLock(String cacheKey) {
        return "ntrain:lock:cache:rebuild:" + cacheKey;
    }

    // ---------- agent 域（一律 ntrain:agent: 前缀，与主仓 key 不串） ----------

    /** 模型配置实例缓存：a_model 行（ModelClientFactory 构造客户端的入参快照） */
    public static String modelConfig(long modelId) {
        return "ntrain:agent:cache:model:" + modelId;
    }

    /** ChatClient 实例缓存 */
    public static String chatClient(long modelId) {
        return "ntrain:agent:cache:chat:client:" + modelId;
    }

    /** ChatModel 实例缓存 */
    public static String chatModel(long modelId) {
        return "ntrain:agent:cache:chat:chat:model:" + modelId;
    }

    /** EmbeddingModel 实例缓存 */
    public static String embeddingModel(long modelId) {
        return "ntrain:agent:cache:chat:embedding:model:" + modelId;
    }

    /** agent 通用缓存 key（namespace 自定，避免散落裸字符串） */
    public static String agentCache(String namespace, String key) {
        return "ntrain:agent:cache:" + namespace + ":" + key;
    }

    /** agent 会话工作记忆：顶层状态（state/agentSub/失败计数），唯一真相源非缓存 */
    public static String memoryState(long conversationId) {
        return "ntrain:agent:memory:state:" + conversationId;
    }

    /** agent 会话工作记忆：业务实体（查询条件/车票快照/待确认单镜像） */
    public static String memoryEntity(long conversationId) {
        return "ntrain:agent:memory:entity:" + conversationId;
    }

    /** agent 工具短期引用（listToken/ticketRef/passengerRef/orderRef → 真实业务对象的映射） */
    public static String agentToolRef(long conversationId) {
        return "ntrain:agent:tool:ref:" + conversationId;
    }
}
