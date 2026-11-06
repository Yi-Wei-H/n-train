package com.weiyi.common.core.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页返回体（与具体 ORM 解耦，common-mybatis 提供与 MP IPage 的互转）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    private long total;
    private long pageNum;
    private long pageSize;
    private List<T> list;

    public static <T> PageResult<T> of(long total, long pageNum, long pageSize, List<T> list) {
        return new PageResult<>(total, pageNum, pageSize, list == null ? Collections.emptyList() : list);
    }

    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return new PageResult<>(0L, pageNum, pageSize, Collections.emptyList());
    }
}
