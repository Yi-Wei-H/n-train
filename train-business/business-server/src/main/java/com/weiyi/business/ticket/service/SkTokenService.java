package com.weiyi.business.ticket.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.redis.constant.RedisKeys;
import com.weiyi.business.ticket.config.SkTokenProperties;
import com.weiyi.business.ticket.infra.entity.BDailyTrainSeat;
import com.weiyi.business.ticket.infra.entity.BDailyTrainTicket;
import com.weiyi.business.ticket.infra.entity.BSkToken;
import com.weiyi.business.ticket.infra.mapper.BDailyTrainSeatMapper;
import com.weiyi.business.ticket.infra.mapper.BDailyTrainTicketMapper;
import com.weiyi.business.ticket.infra.mapper.BSkTokenMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 令牌大闸：按"日期+车次"的 Redis 计数控制进入下单事务的总请求数，
 * 把抢票洪峰挡在车次锁与选座事务之前。
 *
 * <p>一致性契约（宁松勿紧，防超卖始终由锁座事务负责）：
 * <ul>
 *   <li>DB b_sk_token.remaining 为权威剩余，写点只有三个——初始发放、成功订单消耗
 *       （下单事务内条件 UPDATE）、补偿加量</li>
 *   <li>Redis RAtomicLong 为准入计数器，key 不存在时按 DB 剩余量懒重建（TTL 至乘车日结束），
 *       不依赖定时任务；被闸门放行但未成单的请求只耗 Redis 不耗 DB，偏差方向永远偏松</li>
 *   <li>补偿任务只处理"闸门已关（Redis ≤ 0）但该车次仍有余票"：DB 加量后 Redis 直接 SET
 *       为 DB 剩余（不用 INCRBY——拒绝洪峰可能已把计数打到深负）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkTokenService {

    private final BSkTokenMapper skTokenMapper;
    private final BDailyTrainSeatMapper dailySeatMapper;
    private final BDailyTrainTicketMapper dailyTicketMapper;
    private final RedissonClient redissonClient;
    private final SkTokenProperties properties;

    // ==================== 闸门（下单入口，车次锁之外的快路径） ====================

    /** 下单准入：扣不到令牌即拒绝；key 缺失按 DB 懒重建，查无令牌数据视为未初始化放行 */
    public void tryAcquire(LocalDate rideDate, String trainCode) {
        if (!properties.isEnabled()) {
            return;
        }
        RAtomicLong token = token(rideDate, trainCode);
        if (!token.isExists()) {
            log.info("令牌缓存缺失，按 DB 懒重建：{} {}", rideDate, trainCode);
            BSkToken row = loadOrCreate(rideDate, trainCode);
            if (row == null) {
                log.warn("令牌数据不存在，未初始化直接放行：{} {}", rideDate, trainCode);
                return;
            }
            // 并发重建时后写者覆盖同源值，偶发多放一个令牌，方向偏松可接受
            token.set(row.getRemaining());
            expire(token, rideDate);
        }
        long left = token.decrementAndGet();
        if (left < 0) {
            log.info("令牌准入拒绝：{} {} 剩余 {}", rideDate, trainCode, left);
            throw new BusinessException(ErrorCode.SK_TOKEN_EXHAUSTED);
        }
        log.debug("令牌准入放行：{} {} 剩余 {}", rideDate, trainCode, left);
    }

    /** 成功订单消耗令牌（下单事务内调用，车次锁已串行化同车次订单）；扣不动只记日志不拦单 */
    public void consumeInTx(LocalDate rideDate, String trainCode) {
        if (!properties.isEnabled()) {
            return;
        }
        int affected = skTokenMapper.consume(rideDate, trainCode);
        if (affected == 0) {
            log.debug("令牌未扣减（耗尽或无记录）：{} {}", rideDate, trainCode);
        }
    }

    // ==================== 初始发放（每日车次生成时） ====================

    /** 初始发放令牌（幂等：行已存在直接返回；Redis 仅在缺失时预热，避免首单多查库） */
    public void init(LocalDate rideDate, String trainCode) {
        if (!properties.isEnabled()) {
            return;
        }
        BSkToken row = selectBy(rideDate, trainCode);
        if (row == null) {
            row = loadOrCreate(rideDate, trainCode);
        }
        if (row == null) {
            return;
        }
        RAtomicLong token = token(rideDate, trainCode);
        if (!token.isExists()) {
            token.set(row.getRemaining());
            expire(token, rideDate);
        }
    }

    // ==================== 补偿（train-task 定时调用） ====================

    /**
     * 令牌补偿：闸门已关但该车次仍有余票的，DB 加量后以 DB 剩余覆盖 Redis，返回处理车次数。
     * 频率只影响加量及时性，不影响一致性（缓存的懒重建在下单侧完成）。
     */
    public int topup() {
        List<BSkToken> rows = skTokenMapper.selectList(Wrappers.<BSkToken>lambdaQuery()
                .ge(BSkToken::getRideDate, LocalDate.now()));
        int handled = 0;
        for (BSkToken row : rows) {
            RAtomicLong token = token(row.getRideDate(), row.getTrainCode());
            // 缓存缺失不算闸门关：下单时懒加载自会重建
            if (!token.isExists() || token.get() > 0) {
                continue;
            }
            if (sumRemaining(row.getRideDate(), row.getTrainCode()) <= 0) {
                continue;
            }
            skTokenMapper.topup(row.getRideDate(), row.getTrainCode(), properties.getTopupStep());
            BSkToken fresh = selectBy(row.getRideDate(), row.getTrainCode());
            long value = fresh != null ? fresh.getRemaining() : row.getRemaining() + properties.getTopupStep();
            token.set(value);
            expire(token, row.getRideDate());
            handled++;
            log.info("令牌补偿：{} {} 加量 {}，现剩余 {}", row.getRideDate(), row.getTrainCode(),
                    properties.getTopupStep(), value);
        }
        return handled;
    }

    // ==================== 内部 ====================

    /** 重建令牌缓存*/
    private BSkToken loadOrCreate(LocalDate rideDate, String trainCode) {
        BSkToken row = selectBy(rideDate, trainCode);
        /*数据库有记录*/
        if (row != null) {
            return row;
        }
        /*无数据创建数据*/
        /*计算令牌数*/
        long count = computeInitial(rideDate, trainCode);
        if (count <= 0) {
            log.warn("令牌初始量非正，跳过发放：{} {}（座位/站对数据缺失？）", rideDate, trainCode);
            return null;
        }
        BSkToken fresh = new BSkToken();
        fresh.setRideDate(rideDate);
        fresh.setTrainCode(trainCode);
        fresh.setTotalCount((int) count);
        fresh.setRemaining((int) count);
        try {
            skTokenMapper.insert(fresh);
            return fresh;
        } catch (DuplicateKeyException e) {
            return selectBy(rideDate, trainCode);
        }
    }

    /** 初始发放量 = 座位数 × 站对数 × 系数（不低于可售单位上限，闸门只挡注定失败的请求） */
    private long computeInitial(LocalDate rideDate, String trainCode) {
        Long seats = dailySeatMapper.selectCount(Wrappers.<BDailyTrainSeat>lambdaQuery()
                .eq(BDailyTrainSeat::getRideDate, rideDate)
                .eq(BDailyTrainSeat::getTrainCode, trainCode));
        List<Map<String, Object>> pairs = dailyTicketMapper.selectMaps(Wrappers.<BDailyTrainTicket>query()
                .select("DISTINCT from_station, to_station")
                .eq("ride_date", rideDate)
                .eq("train_code", trainCode));
        /* 站对数：一趟车上任意两个站按行车方向组成的“出发-到达”组合，也就是一个可售区间。*/
        long pairCount = pairs == null ? 0 : pairs.size();
        return Math.round(seats * pairCount * properties.getMultiplier());
    }

    /** 该车次全部站对×席别的余票总和（> 0 即仍可售） */
    private long sumRemaining(LocalDate rideDate, String trainCode) {
        List<Map<String, Object>> rows = dailyTicketMapper.selectMaps(Wrappers.<BDailyTrainTicket>query()
                .select("IFNULL(SUM(remaining), 0) AS total")
                .eq("ride_date", rideDate)
                .eq("train_code", trainCode));
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return 0;
        }
        Object value = rows.get(0).get("total");
        return value instanceof Number n ? n.longValue() : 0;
    }

    private BSkToken selectBy(LocalDate rideDate, String trainCode) {
        return skTokenMapper.selectOne(Wrappers.<BSkToken>lambdaQuery()
                .eq(BSkToken::getRideDate, rideDate)
                .eq(BSkToken::getTrainCode, trainCode));
    }

    private RAtomicLong token(LocalDate rideDate, String trainCode) {
        return redissonClient.getAtomicLong(RedisKeys.skToken(rideDate.toString(), trainCode));
    }

    /** TTL 至乘车日结束；跨零点兜底 10 分钟（过期即视为无可售，懒重建会读到剩余 0） */
    private void expire(RAtomicLong token, LocalDate rideDate) {
        Duration ttl = Duration.between(LocalDateTime.now(), rideDate.plusDays(1).atStartOfDay());
        /*特殊情况：现在的时间已经过了乘车日——即“补发昨天/前天车次的令牌”*/
        token.expire(ttl.compareTo(Duration.ZERO) > 0 ? ttl : Duration.ofMinutes(10));
    }
}
