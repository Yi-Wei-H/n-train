package com.weiyi.member.api.dto;

import com.weiyi.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 会员分页查询 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MemberQuery extends PageQuery {

    private String phone;
    private Integer status;
}
