package com.weiyi.business.order.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weiyi.business.order.infra.entity.BTicket;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface BTicketMapper extends BaseMapper<BTicket> {

    /** 过站巡检-上车：随订单转已出行（b_ticket 自带日期车次与上车站，条件更新幂等） */
    int markTraveledByStations(@Param("rideDate") LocalDate rideDate, @Param("trainCode") String trainCode,
                               @Param("stations") List<String> stations,
                               @Param("fromStatus") int fromStatus, @Param("toStatus") int toStatus);
}
