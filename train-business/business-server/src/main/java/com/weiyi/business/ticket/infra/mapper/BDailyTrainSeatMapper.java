package com.weiyi.business.ticket.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weiyi.business.ticket.infra.entity.BDailyTrainSeat;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日座位 mapper：通用 CRUD 走 MP；下单批量占座与区间空闲过滤写在本模块 resources/mapper/BDailyTrainSeatMapper.xml。
 */
public interface BDailyTrainSeatMapper extends BaseMapper<BDailyTrainSeat> {

    /**
     * 批量占座 UPDATE：一次网络往返完成 N 行（CASE id 单语句；新位图由调用方 mark 后传入）。
     */
    int batchUpdateSell(@Param("seats") List<BDailyTrainSeat> seats);

    /**
     * 按区间过滤空闲座位（仅系统分配路径）：谓词为 SeatBitmap.isFree(fromIdx, toIdx) 的 SQL 等价形式，
     * 排序与全量查询一致（carriage_no, row_no, col_no）。走覆盖索引 idx_seat_idle，免回表。
     */
    List<BDailyTrainSeat> selectIdleInRange(@Param("rideDate") LocalDate rideDate,
                                            @Param("trainCode") String trainCode,
                                            @Param("seatType") Integer seatType,
                                            @Param("fromIdx") int fromIdx, @Param("toIdx") int toIdx);
}
