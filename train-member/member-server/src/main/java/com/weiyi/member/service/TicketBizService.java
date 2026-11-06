package com.weiyi.member.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.weiyi.common.redis.constant.RedisKeys;
import com.weiyi.common.redis.service.LogicalCacheValue;
import com.weiyi.common.redis.service.RedisService;
import com.weiyi.business.api.api.TicketQueryApi;
import com.weiyi.business.api.dto.StationDTO;
import com.weiyi.business.api.dto.TicketDTO;
import com.weiyi.member.config.MemberProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * 余票/车站查询：余票走逻辑过期缓存（过期返回旧值 + 单飞异步重建，防热 key 击穿），
 * 下单/退票成功后由 OrderBizService 精准失效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketBizService {

    private final TicketQueryApi ticketQueryApi;
    private final RedisService redisService;
    private final MemberProperties properties;

    public List<StationDTO> stations() {
        List<StationDTO> stations = redisService.getOrLoad(RedisKeys.STATION_CACHE,
                new TypeReference<List<StationDTO>>() {
                },
                Duration.ofHours(1),
                ticketQueryApi::stations);
        log.debug("车站列表查询：返回 {} 个车站", stations.size());
        return stations;
    }

    public List<TicketDTO> query(LocalDate rideDate, String fromStation, String toStation) {
        String key = RedisKeys.ticketCache(rideDate.toString(), fromStation, toStation);
        List<TicketDTO> tickets = redisService.getOrLoadLogical(key,
                new TypeReference<LogicalCacheValue<List<TicketDTO>>>() {
                },
                Duration.ofSeconds(properties.getTicketCacheSeconds()),
                Duration.ofSeconds(properties.getTicketCachePhysicalSeconds()),
                () -> ticketQueryApi.queryTickets(rideDate, fromStation, toStation));
        log.info("余票查询：{} {} → {} 命中 {} 个车次（逻辑 TTL {}s/物理 TTL {}s）", rideDate, fromStation, toStation,
                tickets.size(), properties.getTicketCacheSeconds(), properties.getTicketCachePhysicalSeconds());
        return tickets;
    }

    /** 实时余票（绕过缓存直查数据端）：下单前详情页刷新用，返回该车次全部席别 */
    public List<TicketDTO> queryDetail(LocalDate rideDate, String trainCode,
                                       String fromStation, String toStation) {
        List<TicketDTO> tickets = ticketQueryApi.queryTicketDetail(rideDate, trainCode, fromStation, toStation);
        log.info("实时余票详情：{} {} {} → {} 返回 {} 个车次（绕过缓存）",
                rideDate, trainCode, fromStation, toStation, tickets.size());
        return tickets;
    }

    public void evictTicketCache(LocalDate rideDate, String fromStation, String toStation) {
        redisService.delete(RedisKeys.ticketCache(rideDate.toString(), fromStation, toStation));
        log.info("余票缓存失效：{} {} → {}", rideDate, fromStation, toStation);
    }
}
