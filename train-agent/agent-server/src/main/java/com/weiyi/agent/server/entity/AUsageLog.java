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
 * 用量记录（一次调用一行，不可变：无 updated_at/deleted，业务侧只 INSERT）。
 * 不继承 BaseTimeEntity——该表没有 updated_at 列。
 */
@Data
@TableName("a_usage_log")
public class AUsageLog implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 用户 id */
    private Long userId;

    /** 调用的模型 ID */
    private Long modelId;

    /** 关联会话ID */
    private Long conversationId;

    /** 新增输入 token 数（未命中缓存的输入） */
    private Integer inputTokens;

    /** 缓存命中 token 数 */
    private Integer cachedTokens;

    /** 输出 token 数 */
    private Integer outputTokens;

    /** 调用耗时（毫秒） */
    private Integer latencyMs;

    /** 调用结果：1成功 2失败 */
    private Integer callStatus;

    /** 失败原因 */
    private String errMsg;
}
