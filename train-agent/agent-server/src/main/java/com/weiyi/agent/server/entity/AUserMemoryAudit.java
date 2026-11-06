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
 * 记忆操作审计（追加写）：只记操作要素，不保存被删正文，删除后不可从审计恢复内容。
 */
@Data
@TableName("a_user_memory_audit")
public class AUserMemoryAudit implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** 操作：CREATE/UPDATE/ENABLE/DISABLE/DELETE/CLEAR/SETTING/EXTRACTION_ACCEPT/EXTRACTION_REJECT */
    private String operation;

    private Long memoryId;

    private String category;

    /** 操作方类型：USER/MEMORY_TASK */
    private String operatorType;

    private String requestId;

    /** 必要结果码（如采纳/拒绝原因） */
    private String resultCode;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
