package com.smartlab.global.config;

import com.smartlab.management.service.db.user.CurrentUserPermissionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 为约束域应用后端权限，并将违规日志对外接口限制为只读。
 * 该过滤器运行在安全链之后，复用 JWT 已写入的当前用户身份。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ConstraintAuthorizationFilter extends OncePerRequestFilter {

    private static final String RULE_PATH = "/api/constraint/rule";
    private static final String MODEL_PATH = "/api/constraint/model";
    private static final String VIOLATION_PATH = "/api/constraint/violation";

    private final CurrentUserPermissionService permissionService;

    public ConstraintAuthorizationFilter(CurrentUserPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith(RULE_PATH)
                || path.startsWith(MODEL_PATH)
                || path.startsWith(VIOLATION_PATH));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("OPTIONS".equals(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (path.startsWith(VIOLATION_PATH)) {
                if (!"GET".equals(method)) {
                    writeError(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "违规日志为系统审计记录，只允许查询");
                    return;
                }
                permissionService.require("violation_log", "view");
            } else if (path.startsWith(MODEL_PATH + "/task/")) {
                permissionService.require("task", "view");
            } else if (path.startsWith(MODEL_PATH)) {
                permissionService.require("constraint_rule", "view");
            } else {
                permissionService.require("constraint_rule", ruleAction(method));
            }
            filterChain.doFilter(request, response);
        } catch (AccessDeniedException e) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        }
    }

    private String ruleAction(String method) {
        return switch (method) {
            case "GET" -> "view";
            case "POST" -> "create";
            case "PUT", "PATCH" -> "edit";
            case "DELETE" -> "delete";
            default -> throw new AccessDeniedException("不支持的约束规则操作");
        };
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"" + escape(message) + "\"}");
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
