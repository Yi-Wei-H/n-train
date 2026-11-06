package com.weiyi.admin.dto;

import com.weiyi.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 日志分页查询 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LogQuery extends PageQuery {

    private String username;
    private Integer status;
}
