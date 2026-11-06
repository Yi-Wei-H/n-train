package com.weiyi.common.core.result;

import com.weiyi.common.core.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回体。code=0 表示成功，其余见 {@link ErrorCode}。
 */
@Data
public class Result<T> implements Serializable {

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok() {
        return build(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }

    public static <T> Result<T> ok(T data) {
        return build(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return build(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return build(code, message, null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return build(errorCode.getCode(), message, null);
    }

    public static <T> Result<T> build(int code, String message, T data) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    public boolean isSuccess() {
        return this.code == ErrorCode.SUCCESS.getCode();
    }
}
