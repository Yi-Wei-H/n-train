package com.weiyi.agent.api.dto;

import lombok.Data;

/**
 * 获取上游模型列表入参：与 a_model 的连接字段同构（表单填好连接信息即可拉，测试通过即可原样保存）。
 */
@Data
public class ModelListReq {

    private String baseUrl;

    private String apiKey;

    /** 编辑场景回退用：apiKey 留空且携带 modelId 时，取该模型配置的库存 key 拉取 */
    private Long modelId;

    /** 上游格式：1 OpenAI兼容 3 Anthropic（2/4 无客户端实现，拒绝） */
    private Integer apiFormat;

    /** 认证方式：1 Bearer 2 Key头直传 3 查询参数；apiFormat=1 仅允许 1 */
    private Integer authType;

    /** 认证字段名，authType=2/3 必填；Bearer 时忽略 */
    private String authKeyName;

    /** 可选：按 id/ownedBy/displayName 过滤（contains，忽略大小写） */
    private String keyword;
}
