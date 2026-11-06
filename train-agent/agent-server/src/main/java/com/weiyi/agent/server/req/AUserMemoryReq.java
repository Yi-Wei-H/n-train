package com.weiyi.agent.server.req;

import lombok.Data;

/**
 * 记忆新增/修改入参。客户端不能设置 sourceType、confidence、sourceMessageId、memoryEpoch 与审计字段；
 * 修改 content 时走与新增完全相同的校验与去重。
 */
@Data
public class AUserMemoryReq {

    /** 规范键：小写字母开头的字母数字下划线（新增必填，修改忽略） */
    private String memoryKey;

    /** 取值模式：1单值（默认）2多条 */
    private Integer valueMode;

    /** 分类：TRAVEL_PREFERENCE / COMMUNICATION_PREFERENCE / STABLE_CONSTRAINT / USER_FACT */
    private String category;

    /** 记忆正文 */
    private String content;

    /** 状态：1启用 2停用（修改时可单独提交） */
    private Integer status;

    /** 乐观锁版本（修改必填，冲突提示刷新） */
    private Integer version;
}
