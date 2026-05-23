package com.example.demo.config;

import com.example.demo.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * ── SecurityConfig ──
 * Lõi bảo mật của ứng dụng Spring Boot.
 * <p>
 * 📌 @Configuration + @EnableWebSecurity:
 * Báo Spring đây là class cấu hình security.
 * <p>
 * 📌 SecurityFilterChain:
 * Cách cấu hình mới (thay WebSecurityConfigurerAdapter cũ từ Spring 2.x)
 * Định nghĩa chuỗi filter cho tất cả request.
 * <p>
 * 📌 Luồng hoạt động:
 * Request → JwtAuthFilter → các filter mặc định → Controller
 * │
 * Nếu có token hợp lệ → set Authentication (cho qua)
 * Nếu không có token  → SecurityContext rỗng
 * <p>
 * Với request /api/auth/** → permitAll (không cần auth)
 * Còn lại                → yêu cầu authenticated (401 nếu không có)
 * <p>
 * 📌 SessionCreationPolicy.STATELESS:
 * Không tạo session (ứng dụng stateless, dùng JWT).
 * Spring Security sẽ không tạo HttpSession và không đọc JSESSIONID.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * Inject JwtAuthFilter để thêm vào chuỗi filter.
     */
    private final JwtAuthFilter jwtAuthFilter;

    /**
     * ── SecurityFilterChain ──
     * Cấu hình chính: định nghĩa endpoint nào cần auth, endpoint nào public.
     * <p>
     * ═══════════════════════════════════════════════════════════════════
     * API Endpoint              │  Auth required    │  Ghi chú
     * ═══════════════════════════════════════════════════════════════════
     * POST /api/auth/register   │  ❌ (permitAll)   │  Đăng ký tài khoản
     * POST /api/auth/login      │  ❌ (permitAll)   │  Đăng nhập
     * GET  /api/auth/me         │  ✅ (authenticated)│  Lấy thông tin user
     * GET  /api/hello           │  ✅ (authenticated)│  Health check
     * H2 Console (/h2-console)  │  ❌ (permitAll)   │  Dev tool
     * Tất cả /api/** còn lại    │  ✅ (authenticated)│  API chính
     * ═══════════════════════════════════════════════════════════════════
     * <p>
     * 📌 Các method chính:
     * .csrf().disable()
     * → Tắt CSRF (vì dùng JWT, không dùng session cookie)
     * → Nếu không tắt, POST/PUT/DELETE sẽ bị chặn (403)
     * <p>
     * .sessionManagement()
     * .sessionCreationPolicy(STATELESS)
     * → Không tạo session (phù hợp với REST API + JWT)
     * <p>
     * .authorizeHttpRequests()
     * .requestMatchers("/api/auth/**").permitAll()
     * → Cho phép tất cả request tới /api/auth/... (register, login)
     * <p>
     * .anyRequest().authenticated()
     * → Tất cả request khác phải có xác thực
     * <p>
     * .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
     * → Thêm JwtAuthFilter VÀO TRƯỚC filter mặc định của Spring Security
     * → Đảm bảo JWT được xử lý trước khi kiểm tra authentication
     * <p>
     * 📌 headers().frameOptions().sameOrigin():
     * Cho phép H2 Console hiển thị trong <frame> (mặc định bị chặn vì lý do
     * bảo mật clickjacking). Chỉ dùng cho dev.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\",\"timestamp\":\"%s\"}"
                                            .formatted(java.time.LocalDateTime.now()));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Invalid or missing token\",\"timestamp\":\"%s\"}"
                                            .formatted(java.time.LocalDateTime.now()));
                        })
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * ── PasswordEncoder ──
     * Bean mã hoá password sử dụng BCrypt.
     * <p>
     * 📌 BCrypt:
     * Thuật toán hash một chiều (không thể giải ngược).
     * Tự động thêm salt (muối) → mỗi lần hash cho kết quả khác nhau.
     * `$2a$10$...` → độ phức tạp 10 (càng cao càng lâu, mặc định 10).
     * <p>
     * 📌 Dùng ở đâu?
     * - AuthService: passwordEncoder.encode(password) → khi register
     * - AuthService: passwordEncoder.matches(raw, hash) → khi login
     * <p>
     * 📌 @Bean:
     * Đánh dấu phương thức trả về 1 Bean Spring.
     * Có thể @Inject passwordEncoder vào bất kỳ class nào.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
