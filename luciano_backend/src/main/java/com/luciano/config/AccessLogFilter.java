package com.luciano.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.UUID;

/**
 * API 访问日志过滤器
 * <p>
 * 记录每个请求的方法、路径、状态码、耗时。
 * 错误请求额外记录响应体（最大 2KB）。
 */
@Component
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger accessLog = LoggerFactory.getLogger("ACCESS_LOG");
    private static final Logger errorLog = LoggerFactory.getLogger("ACCESS_ERROR");

    private static final int MAX_ERROR_BODY = 2048;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 跳过静态资源和健康检查
        String uri = request.getRequestURI();
        if (uri.startsWith("/favicon") || uri.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put("traceId", traceId);

        long start = System.currentTimeMillis();
        int status = 0;
        String method = request.getMethod();

        try {
            filterChain.doFilter(request, response);
            status = response.getStatus();
        } catch (Exception e) {
            status = 500;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - start;
            String userId = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "-";
            String clientIp = getClientIp(request);

            // 正常访问日志
            accessLog.info("{} {} {} {} {} {}ms {}",
                    traceId, method, uri, status, userId, clientIp, duration);

            // 4xx/5xx 额外记录详情
            if (status >= 400) {
                String detail = formatErrorDetail(request, response, duration);
                if (status >= 500) {
                    errorLog.error("{} {} {} status={} duration={}ms - {}",
                            traceId, method, uri, status, duration, detail);
                } else {
                    errorLog.warn("{} {} {} status={} duration={}ms - {}",
                            traceId, method, uri, status, duration, detail);
                }
            }

            MDC.remove("traceId");
        }
    }

    private String formatErrorDetail(HttpServletRequest request, HttpServletResponse response, long duration) {
        StringBuilder sb = new StringBuilder();
        sb.append("ip=").append(getClientIp(request));

        // 请求参数（GET 的 queryString 或 POST 的简短参数）
        String qs = request.getQueryString();
        if (qs != null) {
            sb.append(" query=").append(truncate(qs, 200));
        }

        // Content-Type
        String ct = request.getContentType();
        if (ct != null) {
            sb.append(" contentType=").append(ct);
        }

        // Content-Length（上传文件大小）
        long cl = request.getContentLengthLong();
        if (cl > 0) {
            sb.append(" contentLength=").append(formatSize(cl));
        }

        sb.append(" duration=").append(duration).append("ms");

        return sb.toString();
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        // 多层代理时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }
}