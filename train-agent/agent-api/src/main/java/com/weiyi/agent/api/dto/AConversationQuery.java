package com.weiyi.agent.api.dto;

import com.weiyi.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AConversationQuery extends PageQuery {

    /** 会话 id 精确筛选 */
    private Long id;

    /** 属主用户 id 精确筛选 */
    private Long userId;

    /** 状态：1正常 2归档 */
    private Integer status;

    /** 标题模糊筛选 */
    private String title;
}
