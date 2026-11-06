package com.weiyi.agent.api.dto;

import com.weiyi.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AMessageQuery extends PageQuery {

    /** 会话 id：拉取该会话的历史记录 */
    private Long conversationId;

    /** 文字模型 id 筛选 */
    private Long chatModelId;

    /** 触发方筛选：不传=默认排除 system 轮，显式传（如 system）精确筛选 */
    private String role;
}
