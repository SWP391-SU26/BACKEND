package com.courseqa.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter, ObjectMapper mapper)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/forgot-password").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/plans", "/api/payments/vnpay/return", "/api/payments/vnpay/ipn").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/vnpay/orders").hasRole("STUDENT")
                        .requestMatchers("/api/auth/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/semester-workspaces/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/courses", "/api/courses/workspaces").hasRole("ADMIN")
                        .requestMatchers("/api/courses/*/members", "/api/courses/*/publish-checklist").hasRole("ADMIN")
                        .requestMatchers("/api/documents/*/chapter-suggestions").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/documents/personal", "/api/documents/*/submission").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/documents/*/submission").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/documents/*").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/documents/review-queue").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/documents/*/review").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/documents/*/workspace").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/courses/**", "/api/documents/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/courses/**", "/api/documents/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/courses/**", "/api/documents/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/courses/**", "/api/documents/**").hasRole("ADMIN")
                        .requestMatchers("/api/evaluation/**", "/api/fine-tuning/**").hasAnyRole("ADMIN", "RESEARCHER")
                        .requestMatchers(HttpMethod.POST, "/api/rag/**").hasAnyRole("ADMIN", "RESEARCHER")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> writeError(response, mapper, 401, "Authentication required."))
                        .accessDeniedHandler((request, response, error) -> writeError(response, mapper, 403, "You do not have permission to perform this action.")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void writeError(HttpServletResponse response, ObjectMapper mapper, int status, String message)
            throws java.io.IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(), Map.of("success", false, "message", message));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(java.util.List.of("http://localhost:*", "http://127.0.0.1:*"));
        config.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // X-Upload-Offset carries the resumable-upload byte position; without it the
        // browser preflight rejects every chunk PUT.
        config.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type", "X-Upload-Offset"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

}
