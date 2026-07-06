# smartlab_backend_global

Generated at: 2026-07-03T11:09:10

This file is generated from the local SmartLab repository for model-readable project context.

## File Tree

- Backend/src/main/java/com/smartlab/global/auth/AuthenticationTokenService.java
- Backend/src/main/java/com/smartlab/global/config/JwtAuthenticationFilter.java
- Backend/src/main/java/com/smartlab/global/config/MyBatisPlusConfig.java
- Backend/src/main/java/com/smartlab/global/config/SecurityConfig.java
- Backend/src/main/java/com/smartlab/global/config/TaskExecutionConfig.java
- Backend/src/main/java/com/smartlab/global/util/JsonNodeSupport.java
- Backend/src/main/java/com/smartlab/global/util/JsonPathReader.java
- Backend/src/main/java/com/smartlab/global/util/JsonSchemaValidationService.java

## Files

---

## Backend/src/main/java/com/smartlab/global/auth/AuthenticationTokenService.java

````text
package com.smartlab.global.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
/**
 * AuthenticationToken业务持久层核心操作服务。
 */
public class AuthenticationTokenService {

    private final SecretKey secretKey;
    private final long expirationMs;

    public AuthenticationTokenService(@Value("${smartlab.auth.jwt-secret}") String jwtSecret,
                                      @Value("${smartlab.auth.expiration-ms:86400000}") long expirationMs) {
        this.secretKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(secretKey)
                .compact();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean validateToken(String token) {
        try {
            return !extractAllClaims(token).getExpiration().before(new Date());
        } catch (Exception ignored) {
            return false;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

````

---

## Backend/src/main/java/com/smartlab/global/config/JwtAuthenticationFilter.java

````text
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

````

---

## Backend/src/main/java/com/smartlab/global/config/MyBatisPlusConfig.java

````text
package com.smartlab.global.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 全局配置。
 * 当前只启用 PostgreSQL 分页插件。
 */
@Configuration
/**
 * MyBatisPlusConfig 领域实体/配置模型类。
 */
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
}

````

---

## Backend/src/main/java/com/smartlab/global/config/SecurityConfig.java

````text
package com.smartlab.global.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 系统安全配置。
 * 这里负责 HTTP 安全策略、跨域策略和 JWT 过滤器挂载，不放入 management service。
 */
@Configuration
@EnableWebSecurity
/**
 * SecurityConfig 领域实体/配置模型类。
 */
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType("application/json;charset=UTF-8");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"success\":false,\"message\":\"认证失败或登录已过期，请重新登录\"}");
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/user/login", "/api/user/register", "/api/user/verify", "/error").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                );
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

````

---

## Backend/src/main/java/com/smartlab/global/config/TaskExecutionConfig.java

````text
package com.smartlab.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 任务执行线程池配置。
 * 这里只提供线程池 Bean，具体工作流执行逻辑不放在 management 模块。
 */
@Configuration
/**
 * TaskExecutionConfig 领域实体/配置模型类。
 */
public class TaskExecutionConfig {

    @Bean(name = "workflowTaskExecutor")
    public Executor workflowTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("workflow-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}

````

---

## Backend/src/main/java/com/smartlab/global/util/JsonNodeSupport.java

````text
package com.smartlab.global.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class JsonNodeSupport {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonNodeSupport() {
    }

    public static JsonNode toNode(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JsonNode node) {
            return node;
        }
        return MAPPER.valueToTree(value);
    }

    public static ObjectNode objectNode() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode arrayNode() {
        return MAPPER.createArrayNode();
    }
}

````

---

## Backend/src/main/java/com/smartlab/global/util/JsonPathReader.java

````text
package com.smartlab.global.util;

import java.util.Map;

public final class JsonPathReader {

    private JsonPathReader() {
    }

    public static Object read(Map<String, Object> source, String path) {
        if (source == null || path == null || path.trim().isEmpty()) {
            return null;
        }
        String normalized = path.trim();
        if (normalized.startsWith("@")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        Object current = source;
        for (String token : normalized.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(token);
            if (current == null) {
                return null;
            }
        }
        return current;
    }
}

````

---

## Backend/src/main/java/com/smartlab/global/util/JsonSchemaValidationService.java

````text
package com.smartlab.global.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
/**
 * JsonSchemaValidation业务持久层核心操作服务。
 */
public class JsonSchemaValidationService {

    private final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    private final ConcurrentHashMap<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();

    /**
     * 使用 resources/schemas 下的 JSON Schema 校验载荷。
     */
    public void validate(JsonNode payload, String schemaFileName) {
        if (payload == null || payload.isNull()) {
            return;
        }

        JsonSchema schema = schemaCache.computeIfAbsent(schemaFileName, this::loadSchema);
        Set<ValidationMessage> validationMessages = schema.validate(payload);

        if (!validationMessages.isEmpty()) {
            StringBuilder errors = new StringBuilder("JSON Schema 校验失败: " + schemaFileName + "\n");
            for (ValidationMessage message : validationMessages) {
                errors.append("- ").append(message.getMessage()).append("\n");
            }
            throw new IllegalArgumentException(errors.toString());
        }
    }

    private JsonSchema loadSchema(String schemaFileName) {
        try {
            ClassPathResource resource = new ClassPathResource("schemas/" + schemaFileName);
            try (InputStream inputStream = resource.getInputStream()) {
                return factory.getSchema(inputStream);
            }
        } catch (Exception e) {
            throw new RuntimeException("加载 JSON Schema 失败: " + schemaFileName, e);
        }
    }
}

````
