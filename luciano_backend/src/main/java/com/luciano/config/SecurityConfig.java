package com.luciano.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Spring Security 配置
 * JWT 无状态认证 + 白名单路径
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, CorsConfigurationSource corsConfigurationSource) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 白名单：认证接口
                .requestMatchers(AntPathRequestMatcher.antMatcher("/api/v1/auth/register")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/api/v1/auth/login")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/api/v1/auth/refresh")).permitAll()
                // 白名单：公开资产
                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/assets/*/public")).permitAll()
                // 白名单：模型服务商列表公开
                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/model-providers")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/model-providers/*")).permitAll()

                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/model-configs/defaults")).permitAll()
                // 白名单：适配器能力矩阵和 Schema（前端动态表单需要）
                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/adapters")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/adapters/capabilities")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/adapters/*/schema/*")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/adapters/*/schemas")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/adapters/tasks/*")).permitAll()
                // 白名单：临时文件服务（仅开发测试用）
                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/test/video/*")).permitAll()
                // 白名单：工作流 SSE 事件流（通过 query param token 认证）
                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/workflows/executions/*/stream")).permitAll()
                // 白名单：模板列表和节点类型 Schema（前端需要未登录也能获取）
                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/workflows/templates")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/workflows/node-types")).permitAll()
                // 其余路径需要认证
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}