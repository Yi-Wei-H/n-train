package com.weiyi.common.core.result;

import lombok.Data;

import java.io.Serializable;

/** 通用分页查询参数 */
@Data
public class PageQuery implements Serializable {

    private long pageNum = 1;
    private long pageSize = 10;

    public long offset() {
        return (Math.max(pageNum, 1) - 1) * Math.max(pageSize, 1);
    }
}
