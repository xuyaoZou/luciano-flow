package com.luciano.common;

import lombok.Getter;

/**
 * 统一响应包装
 */
@Getter
public class Result<T> {
    private final int code;
    private final String message;
    private final T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "success", data);
    }

    public static <T> Result<T> ok() {
        return new Result<>(0, "success", null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(-1, message, null);
    }
}