package com.weiyi.agent.server.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("a_user_model")
public class AUserModel extends BaseTimeEntity {

    /** 用户 id */
    private Long userId;

    /** 模型 id；空=跟随当前启用的模型（用户暂不能自主选择） */
    private Long modelId;

    /** 状态：1可用 2禁用：不能够 AI 交谈功能 */
    private Integer status;

    /** 每日调用上限（次数）：0=不限；实时频率限制不走此表（Redis 滑窗） */
    private Integer dailyQuota;

    @TableLogic
    private Integer deleted;
}
