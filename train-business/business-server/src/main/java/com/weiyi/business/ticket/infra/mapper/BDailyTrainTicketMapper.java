package com.weiyi.business.ticket.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weiyi.business.ticket.infra.entity.BDailyTrainTicket;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 每日余票 mapper：通用 CRUD 走 MP，原子扣减/回补/改价的条件 UPDATE
 * 写在 resources/mapper/BDailyTrainTicketMapper.xml。
 */
public interface BDailyTrainTicketMapper extends BaseMapper<BDailyTrainTicket> {

    /** 原子扣减余票：remaining >= n 才成功，返回受影响行数（防超卖的核心约束） */
    int deductRemaining(@Param("rideDate") LocalDate rideDate, @Param("trainCode") String trainCode,
                        @Param("fromStation") String fromStation, @Param("toStation") String toStation,
                        @Param("seatType") Integer seatType, @Param("count") int count);

    /** 退票/关单回补余票 */
    int increaseRemaining(@Param("rideDate") LocalDate rideDate, @Param("trainCode") String trainCode,
                          @Param("fromStation") String fromStation, @Param("toStation") String toStation,
                          @Param("seatType") Integer seatType, @Param("count") int count);
}
