package com.weiyi.agent.server.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("a_model_preset")
public class AModelPreset extends BaseTimeEntity {

    private String vendor;

    private String presetName;

    /** 模型类型：1文字 2向量 */
    private Integer modelType;

    private String baseUrl;

    /** 上游格式：1 OpenAI兼容-chat 2 OpenAI兼容-Responses 3 Anthropic 4 Gemini */
    private Integer apiFormat;

    /** 认证方式：1 Bearer头 2 Key头直传 3 查询参数 */
    private Integer authType;

    /** 认证字段名 */
    private String authKeyName;

    private String apiModelId;

    /** 默认参数（JSON 字符串，如 {"temperature":0.7}） */
    private String defaultParams;

    private String description;

    /** 展示排序，小在前 */
    private Integer sortNo;

    /** 状态：1上架 2下架 */
    private Integer status;

    @TableLogic
    private Integer deleted;
}
