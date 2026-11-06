package com.weiyi.agent.server.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 账户记忆设置（每账户一行）：总开关 + 单调递增纪元。
 * 删除/清空/关闭重置递增 memory_epoch，使旧异步提炼任务写入前比对失效，防记忆复活。
 */
@Data
@TableName("a_user_memory_setting")
public class AUserMemorySetting implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long userId;

    /** 总开关：1开启 2关闭（关闭不提炼不注入，已有内容保留） */
    private Integer enabled;

    /** 设置版本（异步写入校验） */
    private Integer version;

    /** 记忆纪元：删除/清空/关闭重置时递增 */
    private Integer memoryEpoch;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
