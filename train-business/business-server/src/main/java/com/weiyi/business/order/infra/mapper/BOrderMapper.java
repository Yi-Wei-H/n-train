package com.weiyi.business.order.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weiyi.business.order.infra.entity.BOrder;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface BOrderMapper extends BaseMapper<BOrder> {

    /** 过站巡检-上车：车已发离的上车站集合内，已支付订单批量转已出行（状态机批量入口调用） */
    int markBoarding(@Param("rideDate") LocalDate rideDate, @Param("trainCode") String trainCode,
                     @Param("stations") List<String> stations,
                     @Param("fromStatus") int fromStatus, @Param("toStatus") int toStatus);

    /** 过站巡检-下车：车已到达的下车站集合内，已出行订单批量转已完成（状态机批量入口调用） */
    int markArrivedDest(@Param("rideDate") LocalDate rideDate, @Param("trainCode") String trainCode,
                        @Param("stations") List<String> stations,
                        @Param("fromStatus") int fromStatus, @Param("toStatus") int toStatus);
}
