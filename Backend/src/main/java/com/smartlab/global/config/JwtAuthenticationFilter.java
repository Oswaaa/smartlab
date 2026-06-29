package com.smartlab.global.config;

import com.smartlab.global.auth.AuthenticationTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

/**
 * JWT 请求过滤器。
 * 从 Authorization 请求头中提取 Bearer Token，并写入 Spring Security 上下文。
 */
@Component
/**
 * JwtAuthenticationFilter 领域实体/配置模型类。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthenticationTokenService authenticationTokenService;

    public JwtAuthenticationFilter(AuthenticationTokenService authenticationTokenService) {
        this.authenticationTokenService = authenticationTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        String token = null;
        String username = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            try {
                username = authenticationTokenService.extractUsername(token);
            } catch (Exception ignored) {
                // Token 解析失败时保持未登录状态，由安全链路统一处理。
            }
        }
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null
                && authenticationTokenService.validateToken(token)) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(username, null, new ArrayList<>())
            );
        }
        filterChain.doFilter(request, response);
    }
}
