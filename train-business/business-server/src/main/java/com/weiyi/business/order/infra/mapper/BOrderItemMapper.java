package com.weiyi.business.order.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weiyi.business.order.infra.entity.BOrderItem;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface BOrderItemMapper extends BaseMapper<BOrderItem> {

    /** 下单明细批量 INSERT（多值单语句，一次网络往返；主键/时间戳由调用方预生成） */
    int insertBatch(@Param("items") List<BOrderItem> items);

    /** 过站巡检-上车：随订单转已出行（经 b_order 锚定日期车次与已过站，不依赖订单新状态，可自愈补账） */
    int markTraveledByStations(@Param("rideDate") LocalDate rideDate, @Param("trainCode") String trainCode,
                               @Param("stations") List<String> stations,
                               @Param("fromStatus") int fromStatus, @Param("toStatus") int toStatus);
}
