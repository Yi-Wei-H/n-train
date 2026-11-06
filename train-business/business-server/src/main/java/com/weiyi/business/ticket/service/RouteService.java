package com.weiyi.business.ticket.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.redis.constant.RedisKeys;
import com.weiyi.common.redis.service.RedisService;
import com.weiyi.business.ticket.dto.Route;
import com.weiyi.business.ticket.infra.entity.BStation;
import com.weiyi.business.ticket.infra.entity.BTrain;
import com.weiyi.business.ticket.infra.entity.BTrainStation;
import com.weiyi.business.ticket.infra.mapper.BStationMapper;
import com.weiyi.business.ticket.infra.mapper.BTrainMapper;
import com.weiyi.business.ticket.infra.mapper.BTrainStationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 车次线路解析：车次号 → 按序站表（编码、时刻、公里数），下单/生成每日车次/过站巡检共用。
 * 站表时刻为 LocalTime，站序中时刻较上一站倒退即跨天（dayOffset +1），折算绝对时间由调用方传入乘车日。
 *
 * <p>线路为三张配置表（b_train/b_train_station/b_station）的派生结果，Redis cache-aside 缓存：
 * key 仅 trainCode（Route 只含相对时刻，与乘车日期无关），TTL 至当日结束；
 * 时刻表调整后由管理端删除 key 懒重建；Redis 不可用直接回源 DB，下单不阻断。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RouteService {

    private static final TypeReference<Route> ROUTE_TYPE = new TypeReference<>() {
    };

    private final BTrainMapper trainMapper;
    private final BTrainStationMapper trainStationMapper;
    private final BStationMapper stationMapper;
    private final RedisService redisService;

    /** 解析线路（缓存优先；业务异常不缓存原样上抛，Redis 异常回源 DB） */
    public Route resolve(String trainCode) {
        String key = RedisKeys.routeCache(trainCode);
        Route cached;
        try {
            cached = redisService.get(key, ROUTE_TYPE);
        } catch (Exception e) {
            log.warn("线路缓存读取失败，回源 DB：trainCode={} {}", trainCode, e.getMessage());
            return resolveFromDb(trainCode);
        }
        if (cached != null) {
            return cached;
        }
        Route route = resolveFromDb(trainCode);
        try {
            redisService.set(key, route, ttlToEndOfDay());
        } catch (Exception e) {
            log.warn("线路缓存回填失败（下次访问重建）：trainCode={} {}", trainCode, e.getMessage());
        }
        return route;
    }

    /** 预热线路缓存（每日车次生成后调用；命中即跳过，失败仅记日志不阻断生成任务） */
    public void warmUp(String trainCode) {
        try {
            String key = RedisKeys.routeCache(trainCode);
            if (redisService.get(key, ROUTE_TYPE) != null) {
                return;
            }
            redisService.set(key, resolveFromDb(trainCode), ttlToEndOfDay());
            log.info("线路缓存预热完成：trainCode={}", trainCode);
        } catch (Exception e) {
            log.warn("线路缓存预热失败（下单侧懒加载兜底）：trainCode={} {}", trainCode, e.getMessage());
        }
    }

    /** 时刻表调整后失效线路缓存（cache-aside：下次访问懒重建；删 key 与重建并发期间允许读到旧值） */
    public void evict(String trainCode) {
        try {
            redisService.delete(RedisKeys.routeCache(trainCode));
            log.info("线路缓存已失效：trainCode={}", trainCode);
        } catch (Exception e) {
            log.warn("线路缓存失效失败（key 过期兜底）：trainCode={} {}", trainCode, e.getMessage());
        }
    }

    /** TTL 至当日结束（次日 00:00）；跨零点兜底 10 分钟，对齐 SkTokenService.expire 既有模式 */
    private Duration ttlToEndOfDay() {
        Duration ttl = Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay());
        return ttl.compareTo(Duration.ZERO) > 0 ? ttl : Duration.ofMinutes(10);
    }

    /** 构建车站时刻表（回源 DB，3 次查询） */
    private Route resolveFromDb(String trainCode) {
        BTrain train = trainMapper.selectOne(Wrappers.<BTrain>lambdaQuery()
                .eq(BTrain::getCode, trainCode));
        if (train == null) {
            throw new BusinessException(ErrorCode.TRAIN_NOT_EXISTS);
        }
        List<BTrainStation> stops = trainStationMapper.selectList(
                Wrappers.<BTrainStation>lambdaQuery().eq(BTrainStation::getTrainId, train.getId()));
        if (stops.isEmpty()) {
            throw new BusinessException(ErrorCode.TRAIN_NOT_EXISTS, "车次未维护经停站");
        }
        stops.sort(Comparator.comparing(BTrainStation::getSort));
        Map<Long, BStation> stationMap = stationMapper.selectBatchIds(
                        stops.stream().map(BTrainStation::getStationId).toList()).stream()
                .collect(Collectors.toMap(BStation::getId, Function.identity()));

        List<String> codes = new ArrayList<>(stops.size());
        List<Route.StopTime> stopTimes = new ArrayList<>(stops.size());
        int dayOffset = 0;
        /* 水位线 */
        LocalTime prev = null;
        for (BTrainStation stop : stops) {
            String code = stationMap.get(stop.getStationId()).getCode();
            LocalTime dep = stop.getDepartureTime();
            LocalTime arr = stop.getArrivalTime();
            if (prev != null) {
                /* 等价于两者取最小值 */
                LocalTime earliest = dep == null ? arr : (arr == null ? dep : dep.isBefore(arr) ? dep : arr);
                if (earliest != null && earliest.isBefore(prev)) {
                    dayOffset++;
                    prev = null; // 跨天后水位线以新一天重新起步，午夜后的后续站点不再误判多加一天
                }
            }
            codes.add(code);
            stopTimes.add(new Route.StopTime(code, dep, arr, dayOffset));
            if (code == null) {
                log.warn("线路解析：经停站对应的车站记录缺失：trainId={} stationId={}", train.getId(), stop.getStationId());
            }
            /* 特殊情况：A 站到达时间是当天但出发时间是次日，水位线应取发/到较大者，避免下站误加一天 */
            if (dep != null && (prev == null || dep.isAfter(prev))) {
                prev = dep;
            }
            if (arr != null && (prev == null || arr.isAfter(prev))) {
                prev = arr;
            }
        }
        log.debug("线路解析完成：trainCode={} trainId={} 站数={} 跨天偏移最大={} 天",
                trainCode, train.getId(), codes.size(), dayOffset);
        return new Route(train.getId(), train.getTrainType(), codes, stopTimes);
    }

    /** 两站间公里数（生成票价用） */
    public int distanceKm(String trainCode, String fromCode, String toCode) {
        BTrain train = trainMapper.selectOne(Wrappers.<BTrain>lambdaQuery()
                .eq(BTrain::getCode, trainCode));
        if (train == null) {
            log.warn("公里数计算：车次不存在，兜底 100km：trainCode={} {} → {}", trainCode, fromCode, toCode);
            return 100;
        }
        Map<Long, BStation> stationByCode = stationMapper.selectList(Wrappers.emptyWrapper()).stream()
                .collect(Collectors.toMap(BStation::getId, Function.identity()));
        List<BTrainStation> stops = trainStationMapper.selectList(
                Wrappers.<BTrainStation>lambdaQuery().eq(BTrainStation::getTrainId, train.getId()));
        Integer fromKm = null;
        Integer toKm = null;
        for (BTrainStation stop : stops) {
            String code = stationByCode.get(stop.getStationId()).getCode();
            if (code.equals(fromCode)) {
                fromKm = stop.getDistanceKm();
            }
            if (code.equals(toCode)) {
                toKm = stop.getDistanceKm();
            }
        }
        if (fromKm == null || toKm == null || toKm <= fromKm) {
            log.warn("公里数计算：站距数据缺失或非法（fromKm={}, toKm={}），兜底 100km：trainCode={} {} → {}",
                    fromKm, toKm, trainCode, fromCode, toCode);
            return 100;
        }
        return toKm - fromKm;
    }
}
