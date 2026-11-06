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
 * 会话内一轮对话（一条=一轮：触发输入 → 工具调用 → AI 回复；不可变：无 updated_at/deleted，业务侧只 INSERT）。
 * 不继承 BaseTimeEntity——该表没有 updated_at 列。
 */
@Data
@TableName("a_message")
public class AMessage implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 会话 id */
    private Long conversationId;

    /** 本轮触发方：user 用户（消息/卡片）/ assistant AI / system 系统自动（如标题生成） */
    private String role;

    /** 本轮输入：用户原文或卡片 prompt_text；system 轮为空 */
    private String userContent;

    /** 喂模型的净化版（展示与历史接口只回原文）；空=回退原文（历史数据） */
    private String userContentMasked;

    /** 喂模型的内容口径：净化版优先，空回退原文 */
    public String modelContent() {
        return userContentMasked != null && !userContentMasked.isBlank() ? userContentMasked : userContent;
    }

    /** 便捷卡片 id：卡片发起的轮次记录来源 */
    private Long cardId;

    /** AI 回复；NULL=本轮调用失败（用户输入仍保留） */
    private String reply;

    /** 本轮调用的工具清单（JSON 数组字符串，预留字段，工具调用未接入前恒空） */
    private String tools;

    /** 文字模型 ID */
    private Long chatModelId;

    /** 关联用量流水 ID（a_usage_log.id，token 用量记录在用量表） */
    private Long usageLogId;

    /** RAG 引用溯源（JSON 字符串，整存整取） */
    private String refs;
}
