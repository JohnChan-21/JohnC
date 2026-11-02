package com.example.common;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 配置类
 * 1. 注册拦截器，定义哪些路由需要登录鉴权
 * 2. 使用 Cookie 传输 token（跨域 Cookie 由 CorsConfig 配合实现）
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * 注册 Sa-Token 拦截器，定义路由鉴权规则
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
     
     
        registry.addInterceptor(new SaInterceptor(handle -> {

            // 全局登录校验：排除登录、注册等公开接口
            SaRouter.match("/**")
                .notMatch(
                        "/login*",
                        "/register*",
                        "/files/download/**"
                        )
                .check(r -> StpUtil.checkLogin());

            // user/notice/category/film 的写操作仅管理员；/user/update 本人放行
            // 注意：/comment/** 不在此列——评论的增/删/改登录即可，归属校验在 CommentService 内完成
            SaRouter.matchMethod("POST","PUT")
                .match("/user/**","/notice/**", "/category/**", "/film/**")
                .notMatch("/user/update")
                .check(r -> StpUtil.checkRole("ADMIN"));


            // 删除操作：user/notice/category/film 仅管理员；评论删除由 Service 校验"本人或管理员"
            SaRouter.matchMethod("DELETE")
                .match("/user/**", "/notice/**", "/category/**", "/film/**")
                .check(r -> StpUtil.checkRole("ADMIN"));

        })).addPathPatterns("/**");
        
        
    }
}
