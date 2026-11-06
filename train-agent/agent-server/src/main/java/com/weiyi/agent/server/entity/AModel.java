package com.weiyi.agent.server.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.weiyi.common.mybatis.handler.AesCryptoTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "a_model", autoResultMap = true)
public class AModel extends BaseTimeEntity{

    private String modelName;

    /** 模型类型：1文字 2向量 */
    private Integer modelType;

    private String provider;

    private String baseUrl;

    /** 上游格式：1 OpenAI兼容-chat 2 OpenAI兼容-Responses 3 Anthropic 4 Gemini */
    private Integer apiFormat;

    /** 认证方式：1 Bearer头 2 Key头直传 3 查询参数 */
    private Integer authType;

    /** 认证字段名 */
    private String authKeyName;

    private String apiModelId;

    @TableField(typeHandler = AesCryptoTypeHandler.class)
    private String apiKey;

    private Long presetId;

    /** 默认参数（JSON 字符串，如 {"temperature":0.7}） */
    private String params;

    /** 状态：1启用 2停用 */
    private Integer status;

    private String remark;

    @TableLogic
    private Integer deleted;
}
