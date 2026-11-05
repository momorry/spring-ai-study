package com.moli.agent.scope.common.base;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApiResult<T> {

    private boolean success;

    private int code;

    private String message;

    private T data;

    // ==================== ok ====================

    public static <T> ApiResult<T> ok() {
        return ApiResult.<T>builder()
                .success(true)
                .code(EnumErrorCode.OK.getCode())
                .message("success")
                .build();
    }

    public static <T> ApiResult<T> ok(T data) {
        return ApiResult.<T>builder()
                .success(true)
                .code(EnumErrorCode.OK.getCode())
                .message("success")
                .data(data)
                .build();
    }

    public static <T> ApiResult<T> ok(String message) {
        return ApiResult.<T>builder()
                .success(true)
                .code(EnumErrorCode.OK.getCode())
                .message(message)
                .build();
    }

    public static <T> ApiResult<T> ok(String message, T data) {
        return ApiResult.<T>builder()
                .success(true)
                .code(EnumErrorCode.OK.getCode())
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResult<T> ok(int code, String message, T data) {
        return ApiResult.<T>builder()
                .success(true)
                .code(code)
                .message(message)
                .data(data)
                .build();
    }

    // ==================== fail ====================

    public static <T> ApiResult<T> fail() {
        return ApiResult.<T>builder()
                .success(false)
                .code(EnumErrorCode.FAIL.getCode())
                .message("服务器内部错误")
                .build();
    }

    public static <T> ApiResult<T> fail(String message) {
        return ApiResult.<T>builder()
                .success(false)
                .code(EnumErrorCode.FAIL.getCode())
                .message(message)
                .build();
    }

    public static <T> ApiResult<T> fail(int code, String message) {
        return ApiResult.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .build();
    }

    public static <T> ApiResult<T> fail(String message, T data) {
        return ApiResult.<T>builder()
                .success(false)
                .code(EnumErrorCode.FAIL.getCode())
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResult<T> fail(int code, String message, T data) {
        return ApiResult.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .data(data)
                .build();
    }

}
