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
 * 账户当前会话指针（DDL 真相源 sql/agent/01_schema.sql）：一账户一行，
 * 指向唯一当前主会话；active_request_id 为主回复占用位，流结束 CAS 释放、
 * 崩溃由占用超时回收。
 */
@Data
@TableName("a_user_chat_state")
public class AUserChatState implements Serializable {

    /** 属主用户 id（主键，应用赋值） */
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    /** 当前主会话 id；空=从未交流或已翻篇 */
    private Long currentConversationId;

    /** 进行中主回复的 clientRequestId；空=空闲 */
    private String activeRequestId;

    /** 占用开始时间（占用超时回收的计时锚点） */
    private LocalDateTime activeSince;

    /** 并发版本（乐观锁预留） */
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
