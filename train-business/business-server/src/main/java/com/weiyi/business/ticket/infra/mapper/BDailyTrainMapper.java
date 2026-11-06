package com.weiyi.business.ticket.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weiyi.business.ticket.infra.entity.BDailyTrain;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalTime;

public interface BDailyTrainMapper extends BaseMapper<BDailyTrain> {

    /** 未发车→运行中：当天已到发车时刻 */
    int markRunning(@Param("today") LocalDate today, @Param("now") LocalTime now);

    /** →已到达：不跨天车次当天到达时刻已过 */
    int markArrivedSameDay(@Param("today") LocalDate today, @Param("now") LocalTime now);

    /** →已到达：跨天车次（到达时刻早于出发时刻即次日到）昨天发的车今天到点 */
    int markArrivedCrossDay(@Param("yesterday") LocalDate yesterday, @Param("now") LocalTime now);

    /** →已到达：前天及更早的车次（含跨天已全部到点） */
    int markArrivedBefore(@Param("deadline") LocalDate deadline);
}
