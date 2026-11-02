package com.example.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.example.common.Result;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;


@ControllerAdvice(basePackages = "com.example.controller")
public class GlobalExceptionHandler {

    private static final Log log = LogFactory.get();

    //统一异常处理@ExceptionHandler,主要用于Exception
    @ExceptionHandler(Exception.class)
    @ResponseBody//返回json串
    public Result error(HttpServletRequest request, Exception e) {
        log.error("异常信息：", e);
        return Result.error();
    }

    @ExceptionHandler(CustomException.class)
    @ResponseBody//返回json串
    public Result customError(HttpServletRequest request, CustomException e) {
        return Result.error(e.getMsg());
    }

    @ExceptionHandler(NotLoginException.class)
    @ResponseBody//返回json串
    public Result notLoginError(HttpServletRequest request, NotLoginException e) {
        // 未登录访问，返回 401 便于前端区分
        log.warn("未登录访问：{} | type={}", request.getRequestURI(), e.getType());
        return Result.errorUnauthorized(        );
    }

    @ExceptionHandler({NotRoleException.class, NotPermissionException.class})
    @ResponseBody//返回json串
    public Result forbiddenError(HttpServletRequest request, Exception e) {
        // 无权限访问（Sa-Token 的 checkRole/checkPermission 失败），返回 403 便于前端区分
        log.warn("无权限访问：{} | msg={}", request.getRequestURI(), e.getMessage());
        return Result.errorForbidden();
    }
    
}
