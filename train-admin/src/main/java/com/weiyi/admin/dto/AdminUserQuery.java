package com.weiyi.admin.dto;

import com.weiyi.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 管理员分页查询 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminUserQuery extends PageQuery {

    private String username;
    private Integer status;
    private Long deptId;
}
