package com.weiyi.business.api.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 令牌大闸接口：补偿加量由 train-task 的 Quartz 定时调用。
 */
@FeignClient(name = "train-business", contextId = "skTokenApi")
public interface SkTokenApi {

    /** 令牌补偿：闸门已关但仍有余票的车次加量并覆盖缓存，返回处理车次数 */
    @PostMapping("/tk/sk-token/topup")
    int topup();
}
