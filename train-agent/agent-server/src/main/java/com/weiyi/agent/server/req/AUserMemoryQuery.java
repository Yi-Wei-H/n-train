package com.weiyi.agent.server.req;

import com.weiyi.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AUserMemoryQuery extends PageQuery {

    /** 分类筛选 */
    private String category;

    /** 状态筛选：1启用 2停用 */
    private Integer status;
}
