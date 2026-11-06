package com.weiyi.agent.server.dto;

import lombok.Data;

import java.util.List;
import com.weiyi.agent.api.dto.AMessageDTO;

/**
 * 用户端 AI 页渲染数据（GET /ai/conversations/current 出参）：
 * 打开页面先做 72h 惰性翻篇判定，再按指针组装——空欢迎态全字段空、
 * 翻篇态返回旧记录（只读，archived=true，前端渲染“新会话”分隔）、
 * 会话态返回当前主会话与标题/占用/禁用状态。不建会话、不写回执。
 */
@Data
public class ChatCurrentVO {

    /** 当前主会话 id；null=从未交流或已翻篇 */
    private Long conversationId;

    /** 会话标题（未生成为占位值） */
    private String title;

    /** 标题状态：NONE/GENERATING/SUCCESS/FAILED */
    private String titleStatus;

    /** 会话锁定的文字模型 id */
    private Long chatModelId;

    /** true=返回的是已翻篇旧记录（只读展示，新会话由下条消息触发创建） */
    private Boolean archived;

    /** true=账户有进行中的主回复任务（多设备/刷新恢复回复态） */
    private Boolean activeRequest;

    /** true=无权限或超日配额，输入区应禁用 */
    private Boolean disabled;

    /** 禁用原因文案（服务端口径） */
    private String disabledReason;

    /** 消息轮次（时间正序，默认只返回最近一页；卡片轮 cardId 非空，前端按卡片主题展示） */
    private List<AMessageDTO> messages;

    /** 是否还有更早的轮次（前端上滑翻页） */
    private Boolean hasMore;
}
