package com.weiyi.business.api.dto;

import com.weiyi.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 车次查询 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TrainQuery extends PageQuery {

    private String code;
}
