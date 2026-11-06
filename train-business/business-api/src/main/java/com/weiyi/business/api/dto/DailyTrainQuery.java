package com.weiyi.business.api.dto;

import com.weiyi.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 每日车次查询 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DailyTrainQuery extends PageQuery {

    private LocalDate rideDate;
    private String trainCode;
}
