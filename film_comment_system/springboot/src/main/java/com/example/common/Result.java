package com.example.common;

import lombok.Data;

@Data
public class Result<T> {

    // 业务码常量
    // 成功
    public static final int CODE_SUCCESS = 200;
    // 失败
    public static final int CODE_ERROR = 500;
    // 未授权
    public static final int CODE_UNAUTHORIZED = 401;
    // 无权限
    public static final int CODE_FORBIDDEN = 403;

    private int code;
    private String msg;
    private T data;

    public Result() {
    }

    private Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> Result<T> success() {
        return new Result<>(CODE_SUCCESS, "请求成功", null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(CODE_SUCCESS, "请求成功", data);
    }

    public static <T> Result<T> error() {
        return new Result<>(CODE_ERROR, "请求失败", null);
    }

    public static <T> Result<T> error(String msg) {
        return new Result<>(CODE_ERROR, msg, null);
    }

    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }
    
    // 未授权
    public static <T> Result<T> errorUnauthorized() {
        return new Result<>(CODE_UNAUTHORIZED, "未登录或登录已过期", null);
    }

    // 无权限
    public static <T> Result<T> errorForbidden() {
        return new Result<>(CODE_FORBIDDEN, "无权限访问", null);
    }
    
}
