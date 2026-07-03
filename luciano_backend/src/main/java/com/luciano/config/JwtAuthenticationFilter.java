package com.luciano.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器
 * 从 Authorization header 解析 JWT，设置 SecurityContext
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = null;

        // 1. 从 Authorization header 取 token
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
        }

        // 2. 从 query parameter 取 token（用于 <video>/<audio> 等无法设 header 的场景）
        if (token == null) {
            token = request.getParameter("token");
        }

        if (token != null && jwtUtil.validateToken(token) && jwtUtil.isAccessToken(token)) {
            Long userId = jwtUtil.getUserId(token);
            String username = jwtUtil.getUsername(token);
            String role = jwtUtil.getRole(token);

            if (userId != null) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                        );
                // 把 username 存到 details 里，供操作日志使用
                authentication.setDetails(java.util.Map.of("username", username));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}