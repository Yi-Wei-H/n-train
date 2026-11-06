package com.weiyi.agent.api.dto;

import com.weiyi.common.core.result.PageQuery;
import lombok.Data;

@Data
public class AModelQuery extends PageQuery {

    /** 模型类型：1文字 2向量 */
    private Integer modelType;
}
