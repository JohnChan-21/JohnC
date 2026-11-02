package com.example.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 安全响应头过滤器：为所有响应统一补上基础安全头。
 * <p>
 * 说明：
 * 1. X-Content-Type-Options=nosniff 关闭浏览器 MIME 嗅探，配合 /files/download 显式声明的
 *    Content-Type，可防止把伪装文件当成脚本/HTML 执行；图片仍能正常内联渲染。
 * 2. X-Frame-Options=DENY 防点击劫持（同时用 CSP frame-ancestors 亦可，这里取其兼容性更广的一种）。
 * 3. Strict-Transport-Security 仅在 HTTPS 下被浏览器采纳（HTTP 下会被忽略），
 *    若由 Nginx 终止 TLS，转发到应用后本头同样会被带出。
 * 4. 前端 SPA 的 CSP 应在 Nginx 层配置（作用于 HTML 文档），后端为 JSON 接口，故此处不设 CSP。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        filterChain.doFilter(request, response);
    }
}
