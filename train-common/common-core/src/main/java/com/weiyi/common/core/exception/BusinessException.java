package com.weiyi.common.core.exception;

import lombok.Getter;

/**
 * 业务异常：只携带错误码与可展示给用户的消息。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BusinessException of(ErrorCode errorCode, String messageFormat, Object... args) {
        return new BusinessException(errorCode, String.format(messageFormat, args));
    }
}
