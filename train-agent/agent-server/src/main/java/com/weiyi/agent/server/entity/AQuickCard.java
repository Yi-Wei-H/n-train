package com.weiyi.agent.server.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("a_quick_card")
public class AQuickCard extends BaseTimeEntity{

    /** 卡片标题 */
    private String cardName;

    /** 点击发送的提示词 */
    private String promptText;

    /** 图标标识；更新传空即清空 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String icon;

    /** 展示排序，小在前 */
    private Integer sortNo;

    /** 状态：1上架 2下架 */
    private Integer status;

    /** 活动开始（空=常驻）；更新传空即清空 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime startAt;

    /** 活动结束（空=常驻）；更新传空即清空 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime endAt;

    @TableLogic
    private Integer deleted;
}
