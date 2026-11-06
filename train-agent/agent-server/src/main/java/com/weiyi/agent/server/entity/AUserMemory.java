package com.weiyi.agent.server.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账户长期记忆（属于账户不属于会话，72h 轮换不清除）。
 * SINGLE 单值键同 key 只一行；MULTI 按内容指纹去重；删除为物理删除（唯一键随之释放）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("a_user_memory")
public class AUserMemory extends BaseTimeEntity {

    /** 所属账户，所有读写强制带此条件 */
    private Long userId;

    /** 规范键：seat_preference / departure_city / response_style 等 */
    private String memoryKey;

    /** 取值模式：1单值 2多条 */
    private Integer valueMode;

    /** 去重键：SINGLE=memory_key；MULTI=规范化内容 SHA-256 */
    private String dedupeKey;

    /** 分类：TRAVEL_PREFERENCE / COMMUNICATION_PREFERENCE / STABLE_CONSTRAINT / USER_FACT */
    private String category;

    /** 单条简洁正文 */
    private String content;

    /** 来源：USER_EXPLICIT / USER_MANUAL / AUTO_EXTRACTED */
    private String sourceType;

    /** 来源消息引用（可空，不复制敏感消息正文） */
    private Long sourceMessageId;

    /** 置信度 0~1，显式用户指令为 1 */
    private BigDecimal confidence;

    /** 状态：1启用 2停用 */
    private Integer status;

    /** 乐观锁版本，异步任务防旧写 */
    private Integer version;

    /** 最近用户确认或系统采纳时间 */
    private LocalDateTime lastConfirmedAt;
}
