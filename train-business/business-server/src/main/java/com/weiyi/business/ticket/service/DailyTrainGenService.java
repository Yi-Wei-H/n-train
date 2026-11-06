package com.weiyi.business.ticket.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.trace.TraceIdSupport;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.common.redis.constant.RedisKeys;
import com.weiyi.common.redis.service.RedisService;
import com.weiyi.business.api.dto.GenerateResultDTO;
import com.weiyi.business.ticket.infra.entity.BDailyTrain;
import com.weiyi.business.ticket.infra.entity.BDailyTrainCarriage;
import com.weiyi.business.ticket.infra.entity.BDailyTrainSeat;
import com.weiyi.business.ticket.infra.entity.BDailyTrainTicket;
import com.weiyi.business.ticket.infra.entity.BStation;
import com.weiyi.business.ticket.infra.entity.BTrain;
import com.weiyi.business.ticket.infra.entity.BTrainCarriage;
import com.weiyi.business.ticket.infra.entity.BTrainSeat;
import com.weiyi.business.ticket.infra.entity.BTrainStation;
import com.weiyi.business.ticket.infra.mapper.BDailyTrainCarriageMapper;
import com.weiyi.business.ticket.infra.mapper.BDailyTrainMapper;
import com.weiyi.business.ticket.infra.mapper.BDailyTrainSeatMapper;
import com.weiyi.business.ticket.infra.mapper.BDailyTrainTicketMapper;
import com.weiyi.business.ticket.infra.mapper.BStationMapper;
import com.weiyi.business.ticket.infra.mapper.BTrainCarriageMapper;
import com.weiyi.business.ticket.infra.mapper.BTrainMapper;
import com.weiyi.business.ticket.infra.mapper.BTrainSeatMapper;
import com.weiyi.business.ticket.infra.mapper.BTrainStationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 每日车次物化生成：模板车次 → 当日车厢/座位/余票票价。幂等（已存在跳过）。
 * 互斥占位防并发重复生成（Quartz 同步与 管理端手动异步触发共用）。
 * 管理端路径：主线程只做必要校验 + 占执行位，生成在后台虚拟线程执行（同步全量生成可达分钟级，
 * 同步等待会撑爆 HTTP 超时）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyTrainGenService {

    /** 生成执行位租期：覆盖最慢的全量生成时长，进程崩溃后到期自动释放 */
    private static final long GEN_RUNNING_TTL_MS = 10 * 60 * 1000L;

    /** 后台生成线程：重阻塞 JDBC 批量写入、触发频率低，虚拟线程按任务起线程即可，无需池化 */
    private static final ExecutorService GEN_EXECUTOR = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("daily-train-gen-", 0).factory());

    private final BTrainMapper trainMapper;
    private final BTrainStationMapper trainStationMapper;
    private final BTrainCarriageMapper carriageMapper;
    private final BTrainSeatMapper seatMapper;
    private final BStationMapper stationMapper;
    private final BDailyTrainMapper dailyTrainMapper;
    private final BDailyTrainCarriageMapper dailyCarriageMapper;
    private final BDailyTrainSeatMapper dailySeatMapper;
    private final BDailyTrainTicketMapper dailyTicketMapper;
    private final SkTokenService skTokenService;
    private final RouteService routeService;
    private final RedisService redisService;
    private final TransactionTemplate transactionTemplate;

    /** 同步全量生成（train-task 的 Quartz 调用）：同日期与管理端手动触发互斥 */
    public GenerateResultDTO generateForDate(LocalDate rideDate) {
        String runningKey = acquireRunning(rideDate);
        try {
            GenerateResultDTO result = transactionTemplate.execute(status -> doGenerate(rideDate));
            log.info("每日车次生成完成：rideDate={} 车次 {} 列/车厢 {} 节/座位 {} 个/余票票价 {} 条",
                    rideDate, result.getTrainCount(), result.getCarriageCount(),
                    result.getSeatCount(), result.getTicketCount());
            return result;
        } finally {
            redisService.delete(runningKey);
        }
    }

    /**
     * 管理端提交生成（异步）：主线程校验必要条件并占执行位后立即返回，
     * 生成在后台虚拟线程执行。trainCode 省略 = 该日全部车次（幂等跳过已有）。
     */
    public void submitGenerate(LocalDate rideDate, String trainCode) {
        String code = normalizeCode(trainCode);
        String runningKey = acquireRunning(rideDate);
        try {
            if (code != null) {
                requireTrainTemplate(code);
                BizAssert.isTrue(!existsDaily(rideDate, code), ErrorCode.DATA_CONFLICT,
                        "车次 " + code + " 该日已生成，覆盖请使用重新生成");
            }
        } catch (Exception e) {
            redisService.delete(runningKey);
            throw e;
        }
        runBackground(runningKey, () -> {
            GenerateResultDTO result = transactionTemplate.execute(status ->
                    code == null ? doGenerate(rideDate) : doGenerateOne(rideDate, code));
            log.info("每日车次生成完成：rideDate={} trainCode={} 车次 {} 列/车厢 {} 节/座位 {} 个/余票票价 {} 条",
                    rideDate, code == null ? "全部" : code, result.getTrainCount(), result.getCarriageCount(),
                    result.getSeatCount(), result.getTicketCount());
        });
    }

    /**
     * 管理端提交重新生成（异步，必须指定车次）：删该车次该日旧数据后重建。
     * 已售出的旧车票不随之失效（订单侧引用悬空），由前端二次确认兜底。
     */
    public void submitRegenerate(LocalDate rideDate, String trainCode) {
        String code = normalizeCode(trainCode);
        BizAssert.isTrue(code != null, ErrorCode.PARAM_ERROR, "重新生成必须指定车次");
        String runningKey = acquireRunning(rideDate);
        try {
            requireTrainTemplate(code);
        } catch (Exception e) {
            redisService.delete(runningKey);
            throw e;
        }
        runBackground(runningKey, () -> {
            GenerateResultDTO result = transactionTemplate.execute(status -> {
                deleteDailyOf(rideDate, code);
                return doGenerateOne(rideDate, code);
            });
            log.info("每日车次重新生成完成：rideDate={} trainCode={} 车厢 {} 节/座位 {} 个/余票票价 {} 条",
                    rideDate, code, result.getCarriageCount(), result.getSeatCount(), result.getTicketCount());
        });
    }

    /** 占用该日期的生成执行位：同一日期全量/单车/重新生成串行互斥 */
    private String acquireRunning(LocalDate rideDate) {
        String key = RedisKeys.dailyGenRunning(rideDate.toString());
        if (!redisService.setIfAbsent(key, "1", GEN_RUNNING_TTL_MS)) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT, "该日期已有生成任务执行中，请稍后再试");
        }
        return key;
    }

    /** 后台执行生成任务：接力 traceId、异常兜底记录、收尾释放执行位 */
    private void runBackground(String runningKey, Runnable work) {
        String traceId = TraceIdSupport.current();
        GEN_EXECUTOR.execute(() -> {
            TraceIdSupport.bind(traceId);
            try {
                work.run();
            } catch (Exception e) {
                log.error("每日车次后台生成任务失败：{}", e.getMessage(), e);
            } finally {
                TraceIdSupport.clear();
                redisService.delete(runningKey);
            }
        });
    }

    private String normalizeCode(String trainCode) {
        return trainCode == null || trainCode.isBlank() ? null : trainCode.trim();
    }

    private boolean existsDaily(LocalDate rideDate, String trainCode) {
        return dailyTrainMapper.selectCount(Wrappers.<BDailyTrain>lambdaQuery()
                .eq(BDailyTrain::getRideDate, rideDate)
                .eq(BDailyTrain::getTrainCode, trainCode)) > 0;
    }

    private BTrain requireTrainTemplate(String trainCode) {
        BTrain train = trainMapper.selectOne(Wrappers.<BTrain>lambdaQuery()
                .eq(BTrain::getCode, trainCode));
        BizAssert.isTrue(train != null, ErrorCode.TRAIN_NOT_EXISTS, "车次模板不存在：" + trainCode);
        return train;
    }

    /** 生成单车次（含模板车次/车站映射的现查现用） */
    private GenerateResultDTO doGenerateOne(LocalDate rideDate, String trainCode) {
        BTrain train = requireTrainTemplate(trainCode);
        Map<Long, BStation> stationById = stationMapper.selectList(Wrappers.emptyWrapper()).stream()
                .collect(Collectors.toMap(BStation::getId, Function.identity()));
        return generateOne(rideDate, train, stationById);
    }

    /** 删除该车次该日的全部每日数据（余票票价/座位/车厢/车次行；与调用方的事务合一） */
    private void deleteDailyOf(LocalDate rideDate, String trainCode) {
        dailyTicketMapper.delete(Wrappers.<BDailyTrainTicket>lambdaQuery()
                .eq(BDailyTrainTicket::getRideDate, rideDate)
                .eq(BDailyTrainTicket::getTrainCode, trainCode));
        dailySeatMapper.delete(Wrappers.<BDailyTrainSeat>lambdaQuery()
                .eq(BDailyTrainSeat::getRideDate, rideDate)
                .eq(BDailyTrainSeat::getTrainCode, trainCode));
        dailyCarriageMapper.delete(Wrappers.<BDailyTrainCarriage>lambdaQuery()
                .eq(BDailyTrainCarriage::getRideDate, rideDate)
                .eq(BDailyTrainCarriage::getTrainCode, trainCode));
        dailyTrainMapper.delete(Wrappers.<BDailyTrain>lambdaQuery()
                .eq(BDailyTrain::getRideDate, rideDate)
                .eq(BDailyTrain::getTrainCode, trainCode));
        log.info("每日车次重新生成：已删除旧数据 rideDate={} trainCode={}", rideDate, trainCode);
    }

    /** 到点状态扫描：未发车→运行中→已到达（train-task 每分钟触发），返回更新行数 */
    public int sweepStatus() {
        LocalDate today = LocalDate.now();
        java.time.LocalTime now = java.time.LocalTime.now();
        int total = 0;
        total += dailyTrainMapper.markRunning(today, now);
        total += dailyTrainMapper.markArrivedSameDay(today, now);
        total += dailyTrainMapper.markArrivedCrossDay(today.minusDays(1), now);
        total += dailyTrainMapper.markArrivedBefore(today.minusDays(1));
        if (total > 0) {
            log.info("车次状态到点扫描：共推进 {} 行", total);
        }
        return total;
    }

    private GenerateResultDTO doGenerate(LocalDate rideDate) {
        List<BTrain> trains = trainMapper.selectList(Wrappers.emptyWrapper());
        log.info("每日车次生成开始：rideDate={} 模板车次 {} 列", rideDate, trains.size());
        Map<Long, BStation> stationById = stationMapper.selectList(Wrappers.emptyWrapper()).stream()
                .collect(Collectors.toMap(BStation::getId, Function.identity()));

        GenerateResultDTO result = new GenerateResultDTO();
        result.setRideDate(rideDate);
        for (BTrain train : trains) {
            Long exists = dailyTrainMapper.selectCount(Wrappers.<BDailyTrain>lambdaQuery()
                    .eq(BDailyTrain::getRideDate, rideDate)
                    .eq(BDailyTrain::getTrainCode, train.getCode()));
            if (exists > 0) {
                log.debug("每日车次已存在，跳过：rideDate={} trainCode={}", rideDate, train.getCode());
                continue;
            }
            result.accumulate(generateOne(rideDate, train, stationById));
        }
        return result;
    }

    private GenerateResultDTO generateOne(LocalDate rideDate, BTrain train, Map<Long, BStation> stationById) {
        log.info("开始生成每日车次：rideDate={} trainCode={}", rideDate, train.getCode());
        List<BTrainStation> stops = trainStationMapper.selectList(Wrappers.<BTrainStation>lambdaQuery()
                .eq(BTrainStation::getTrainId, train.getId())
                .orderByAsc(BTrainStation::getSort));
        BizAssert.isTrue(!stops.isEmpty(), ErrorCode.TRAIN_NOT_EXISTS,
                "车次 " + train.getCode() + " 未维护经停站，跳过前请先维护");
        List<BTrainCarriage> carriages = carriageMapper.selectList(Wrappers.<BTrainCarriage>lambdaQuery()
                .eq(BTrainCarriage::getTrainId, train.getId())
                .orderByAsc(BTrainCarriage::getCarriageNo));
        List<BTrainSeat> seats = seatMapper.selectList(Wrappers.<BTrainSeat>lambdaQuery()
                .eq(BTrainSeat::getTrainId, train.getId()));
        if (seats.isEmpty() && !carriages.isEmpty()) {
            // 模板座位缺失时按车厢模板兜底生成（省去管理端必须先点"生成座位"）
            log.warn("每日车次生成：模板座位缺失，按车厢模板兜底生成：rideDate={} trainCode={}",
                    rideDate, train.getCode());
            long stationCount = trainStationMapper.selectCount(
                    Wrappers.<BTrainStation>lambdaQuery().eq(BTrainStation::getTrainId, train.getId()));
            if (stationCount > 0) {
                List<BTrainSeat> generated = new ArrayList<>();
                String emptySell = "0".repeat((int) stationCount);
                for (BTrainCarriage carriage : carriages) {
                    for (int row = 1; row <= carriage.getRowCount(); row++) {
                        for (String col : SeatLayouts.colsOf(carriage.getSeatType())) {
                            BTrainSeat seat = new BTrainSeat();
                            seat.setTrainId(train.getId());
                            seat.setCarriageNo(carriage.getCarriageNo());
                            seat.setRowNo(row);
                            seat.setColNo(col);
                            seat.setSeatType(carriage.getSeatType());
                            seat.setSell(emptySell);
                            seatMapper.insert(seat);
                            generated.add(seat);
                        }
                    }
                }
                seats = generated;
            }
        }

        GenerateResultDTO result = new GenerateResultDTO();
        result.setRideDate(rideDate);
        if (seats.isEmpty()) {
            log.warn("每日车次生成：车次无座位模板（座位/车厢模板均缺失），将无可售余票：rideDate={} trainCode={}",
                    rideDate, train.getCode());
        }

        BTrainStation first = stops.get(0);
        BTrainStation last = stops.get(stops.size() - 1);
        BDailyTrain daily = new BDailyTrain();
        daily.setRideDate(rideDate);
        daily.setTrainCode(train.getCode());
        daily.setTrainType(train.getTrainType());
        daily.setDepartureStation(stationById.get(first.getStationId()).getCode());
        daily.setArrivalStation(stationById.get(last.getStationId()).getCode());
        daily.setDepartureTime(first.getDepartureTime());
        daily.setArrivalTime(last.getArrivalTime());
        daily.setStatus(0);
        dailyTrainMapper.insert(daily);
        result.setTrainCount(result.getTrainCount() + 1);

        // 车厢
        for (BTrainCarriage carriage : carriages) {
            BDailyTrainCarriage dc = new BDailyTrainCarriage();
            dc.setRideDate(rideDate);
            dc.setTrainCode(train.getCode());
            dc.setCarriageNo(carriage.getCarriageNo());
            dc.setSeatType(carriage.getSeatType());
            dc.setSeatCount(carriage.getSeatCount());
            dc.setRowCount(carriage.getRowCount());
            dailyCarriageMapper.insert(dc);
            result.setCarriageCount(result.getCarriageCount() + 1);
        }

        // 座位（位图原样复制）
        Map<Integer, Integer> seatsByType = seats.stream()
                .collect(Collectors.groupingBy(BTrainSeat::getSeatType, Collectors.summingInt(s -> 1)));
        for (BTrainSeat seat : seats) {
            BDailyTrainSeat ds = new BDailyTrainSeat();
            ds.setRideDate(rideDate);
            ds.setTrainCode(train.getCode());
            ds.setCarriageNo(seat.getCarriageNo());
            ds.setRowNo(seat.getRowNo());
            ds.setColNo(seat.getColNo());
            ds.setSeatType(seat.getSeatType());
            ds.setSell(seat.getSell());
            dailySeatMapper.insert(ds);
            result.setSeatCount(result.getSeatCount() + 1);
        }

        // 余票票价：站对 × 席别，remaining = 该席别座位数，价格按公里数计算
        List<String> codes = stops.stream()
                .map(s -> stationById.get(s.getStationId()).getCode()).toList();
        Map<String, Integer> kmByCode = stops.stream()
                .collect(Collectors.toMap(s -> stationById.get(s.getStationId()).getCode(),
                        s -> s.getDistanceKm() == null ? 0 : s.getDistanceKm()));
        List<BDailyTrainTicket> tickets = new ArrayList<>();
        for (int i = 0; i < codes.size(); i++) {
            for (int j = i + 1; j < codes.size(); j++) {
                String from = codes.get(i);
                String to = codes.get(j);
                int km = Math.max(kmByCode.get(to) - kmByCode.get(from), 1);
                for (Integer seatType : seatsByType.keySet()) {
                    BDailyTrainTicket t = new BDailyTrainTicket();
                    t.setRideDate(rideDate);
                    t.setTrainCode(train.getCode());
                    t.setFromStation(from);
                    t.setToStation(to);
                    t.setDepartureTime(stops.get(i).getDepartureTime());
                    t.setArrivalTime(stops.get(j).getArrivalTime());
                    t.setSeatType(seatType);
                    t.setRemaining(seatsByType.get(seatType));
                    t.setPrice(PriceUtil.compute(km, train.getTrainType(), seatType));
                    tickets.add(t);
                }
            }
        }
        tickets.forEach(dailyTicketMapper::insert);
        result.setTicketCount(result.getTicketCount() + tickets.size());
        log.debug("每日车次生成明细：{} {} 车厢 {} 节 座位 {} 个 余票票价 {} 条",
                rideDate, train.getCode(), result.getCarriageCount(), result.getSeatCount(), tickets.size());
        // 令牌大闸：初始发放 = 座位 × 站对 × 系数（幂等，行已存在跳过）
        skTokenService.init(rideDate, train.getCode());
        // 线路缓存预热：下单/释放/巡检首访免回源（失败仅记日志，懒加载兜底）
        routeService.warmUp(train.getCode());
        return result;
    }
}
