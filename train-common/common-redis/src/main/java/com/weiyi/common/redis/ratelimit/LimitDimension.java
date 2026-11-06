package com.weiyi.common.redis.ratelimit;

/**
 * 限流维度。USER 取不到登录态回退匿名桶；DEVICE 未携带指纹头回退 IP（防未升级客户端挤进同一桶）。
 */
public enum LimitDimension {

    /** 全局共享一个窗口 */
    GLOBAL,
    /** 按客户端 IP */
    IP,
    /** 按当前登录用户 */
    USER,
    /** 按设备指纹（对抗同设备多账号：指纹缺失回退 IP） */
    DEVICE
}
