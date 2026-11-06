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
@TableName("a_conversation")
public class AConversation extends BaseTimeEntity{

    /** 属主用户 id */
    private Long userId;

    /** 会话标题 */
    private String title;

    /** 文字模型 ID；空=跟随当前启用模型；更新传空即清空 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long chatModelId;

    /** 消息数（聚合字段，消息写入链路维护，不经会话接口修改） */
    private Integer messageCount;

    /** 最近消息时间（聚合字段，消息写入链路维护，不经会话接口修改） */
    private LocalDateTime lastMessageAt;

    /** 最近有效用户交互时间（翻篇判定锚点，仅发送链路刷新；标题/系统任务不更新） */
    private LocalDateTime lastInteractionAt;

    /** 标题状态：0未触发 1生成中 2成功 3最终失败 */
    private Integer titleStatus;

    /** 标题任务首次触发时间（每会话只触发一次） */
    private LocalDateTime titleTriggeredAt;

    /** 当前会话压缩摘要（不跨会话，异步任务写入；新会话不继承） */
    private String summary;

    /** 摘要覆盖到的消息水位（异步任务写入前校验不后退） */
    private Long summaryMessageId;

    /** 状态：1正常 2归档 */
    private Integer status;

    @TableLogic
    private Integer deleted;
}
