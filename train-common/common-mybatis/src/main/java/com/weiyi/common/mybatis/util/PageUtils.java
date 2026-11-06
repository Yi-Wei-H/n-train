package com.weiyi.common.mybatis.util;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.weiyi.common.core.result.PageResult;

import java.util.function.Function;

/**
 * MP IPage ↔ 项目统一 PageResult 互转。
 */
public final class PageUtils {

    private PageUtils() {
    }

    public static <E, R> PageResult<R> convert(IPage<E> page, Function<E, R> converter) {
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                page.getRecords().stream().map(converter).toList());
    }

    public static <E> PageResult<E> of(IPage<E> page) {
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }
}
