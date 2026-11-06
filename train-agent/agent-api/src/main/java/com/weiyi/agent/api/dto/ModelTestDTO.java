package com.weiyi.agent.api.dto;

import lombok.Data;

@Data
public class ModelTestDTO {
    /** 是否连通 */
    private Boolean success;
    /** 调用耗时（毫秒） */
    private Integer latencyMs;
    /** 失败原因，成功时为 null。截断 512，对齐 a_usage_log.err_msg */
    private String errMsg;
    /** 向量维度，仅向量模型测试成功时有值；chat 测试为 null。可暴露与 vector_store 维度不匹配的问题 */
    private Integer dimension;
}
