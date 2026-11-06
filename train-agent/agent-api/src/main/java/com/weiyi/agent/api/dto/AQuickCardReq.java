package com.weiyi.agent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AQuickCardReq {

    /** 空=新增 */
    private Long id;

    @NotBlank(message = "卡片标题不能为空")
    @Size(max = 32, message = "卡片标题不能超过 32 字")
    private String cardName;

    @NotBlank(message = "提示词不能为空")
    @Size(max = 512, message = "提示词不能超过 512 字")
    private String promptText;

    /** 图标标识；更新传空即清空 */
    @Size(max = 64, message = "图标标识不能超过 64 字")
    private String icon;

    /** 展示排序，小在前；空=0 */
    private Integer sortNo;

    /** 状态：1上架 2下架；新增空=上架，更新空=保持原值 */
    private Integer status;

    /** 活动开始；空=常驻；更新传空即清空 */
    private LocalDateTime startAt;

    /** 活动结束；空=常驻；更新传空即清空 */
    private LocalDateTime endAt;
}
