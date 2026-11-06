package com.weiyi.business.order.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.weiyi.common.redis.constant.RedisKeys;
import com.weiyi.common.redis.service.RedisService;
import com.weiyi.business.ticket.infra.entity.BStation;
import com.weiyi.business.ticket.infra.mapper.BStationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 车站编码→站名解析（订单展示用）：同库直查列车域 b_station + Redis 缓存 1 小时。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StationNameResolver {

    private final BStationMapper stationMapper;
    private final RedisService redisService;

    public Map<String, String> nameByCode() {
        return redisService.getOrLoad(RedisKeys.STATION_NAME_MAP,
                new TypeReference<Map<String, String>>() {
                },
                Duration.ofHours(1),
                () -> {
                    List<BStation> stations = stationMapper.selectList(null);
                    log.debug("站名映射缓存回源：加载 {} 条车站记录", stations.size());
                    if (stations.isEmpty()) {
                        log.warn("站名映射缓存回源：车站表为空，订单站名将无法解析");
                    }
                    return stations.stream()
                            .collect(Collectors.toMap(BStation::getCode, BStation::getName));
                });
    }

    public String name(String code) {
        String name = nameByCode().get(code);
        if (name == null) {
            log.warn("站名解析失败（车站表缺该编码或缓存不含该站），回退显示编码：{}", code);
        }
        return name == null ? code : name;
    }
}
