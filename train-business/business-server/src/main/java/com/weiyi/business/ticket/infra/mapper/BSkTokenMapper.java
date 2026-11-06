package com.weiyi.business.ticket.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weiyi.business.ticket.infra.entity.BSkToken;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 令牌大闸 mapper：发放/查询走 MP，消耗与补偿的原子 UPDATE 写在 resources/mapper/BSkTokenMapper.xml。
 */
public interface BSkTokenMapper extends BaseMapper<BSkToken> {

    /** 成功订单消耗令牌：remaining > 0 才命中，返回受影响行数（扣不动由调用方记日志，不拦单） */
    int consume(@Param("rideDate") LocalDate rideDate, @Param("trainCode") String trainCode);

    /** 补偿加量：累计发放与剩余同步加（只加不减） */
    int topup(@Param("rideDate") LocalDate rideDate, @Param("trainCode") String trainCode,
              @Param("step") int step);
}
