package com.weiyi.agent.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 获取上游模型列表结果："拉不到"是正常降级路径（HTTP 200 + success=false），仅入参校验失败走 Result.fail。
 */
@Data
public class ModelListDTO {

    /** 是否拉取成功 */
    private Boolean success;

    /** 失败原因（归类文案），成功为 null */
    private String errMsg;

    /** 成功时有值，按 id 字典序排序 */
    private List<ModelItemDTO> models;

    /** 目录超上限被截断时 true（上限 1000 条） */
    private Boolean truncated;
}
