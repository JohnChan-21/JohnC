package com.example.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 跨域配置
 * 允许前端跨域携带 Cookie（Sa-Token 通过 Cookie 传输 token）。
 * 允许的源由配置项 cors.allowed-origins 维护（逗号分隔，支持通配模式，如 https://*.example.com）：
 * - dev：application-dev.yml 默认放行 http://localhost:5173 / http://127.0.0.1:5173；
 * - prod：application-prod.yml 默认留空——同源反代（Nginx 前后端同域）无需 CORS；
 *   若前后端分域名部署，用环境变量 CORS_ALLOWED_ORIGINS 注入真实前端域名。
 */
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        // 允许指定源（携带 Cookie 时不能使用 *）；注意不能带结尾斜杠，否则 origin 匹配不生效
        // 列表为空（同源反代）时不放行任何跨域源，跨域请求会被浏览器拦截——默认即安全
        for (String origin : allowedOrigins.split(",")) {
            String trimmed = origin.trim();
            if (!trimmed.isEmpty()) {
                corsConfiguration.addAllowedOriginPattern(trimmed);
            }
        }
        // 允许所有请求头
        corsConfiguration.addAllowedHeader("*");
        // 允许所有请求方法
        corsConfiguration.addAllowedMethod("*");
        // 关键：允许携带凭证（Cookie）——鉴权依赖 http-only Cookie（Sa-Token 通过 Cookie 传输 token）；
        // 注意：withCredentials 已开启，前端需要设置 withCredentials 为 true 才能携带 Cookie
        corsConfiguration.setAllowCredentials(true);
        source.registerCorsConfiguration("/**", corsConfiguration);
        return new CorsFilter(source);
    }
}