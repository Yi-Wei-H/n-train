package com.weiyi.business.ticket.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weiyi.business.ticket.infra.entity.BSeatOccupy;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface BSeatOccupyMapper extends BaseMapper<BSeatOccupy> {

    /** 下单占用记录批量 INSERT（多值单语句，一次网络往返；主键由调用方预生成） */
    int insertBatch(@Param("items") List<BSeatOccupy> items);
}
