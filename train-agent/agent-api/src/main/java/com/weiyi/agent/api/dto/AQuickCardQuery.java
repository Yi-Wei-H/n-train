package com.weiyi.agent.api.dto;

import com.weiyi.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AQuickCardQuery extends PageQuery {

    /** 卡片标题模糊筛选 */
    private String cardName;

    /** 状态：1上架 2下架 */
    private Integer status;
}
