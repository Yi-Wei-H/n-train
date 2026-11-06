package com.weiyi.common.core.util;

import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;

import java.util.Collection;
import java.util.Objects;

/**
 * 业务断言：条件不满足直接抛 BusinessException。
 */
public final class BizAssert {

    private BizAssert() {
    }

    public static void isTrue(boolean condition, ErrorCode errorCode) {
        if (!condition) {
            throw new BusinessException(errorCode);
        }
    }

    public static void isTrue(boolean condition, ErrorCode errorCode, String message) {
        if (!condition) {
            throw new BusinessException(errorCode, message);
        }
    }

    public static void notNull(Object obj, ErrorCode errorCode) {
        isTrue(obj != null, errorCode);
    }

    public static void notNull(Object obj, ErrorCode errorCode, String message) {
        isTrue(obj != null, errorCode, message);
    }

    public static void notBlank(String text, ErrorCode errorCode) {
        isTrue(text != null && !text.isBlank(), errorCode);
    }

    public static void notBlank(String text, ErrorCode errorCode, String message) {
        isTrue(text != null && !text.isBlank(), errorCode, message);
    }

    public static void notEmpty(Collection<?> collection, ErrorCode errorCode) {
        isTrue(collection != null && !collection.isEmpty(), errorCode);
    }

    public static void equal(Object expected, Object actual, ErrorCode errorCode) {
        isTrue(Objects.equals(expected, actual), errorCode);
    }
}
