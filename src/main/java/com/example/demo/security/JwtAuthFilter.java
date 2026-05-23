package com.example.demo.security;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * ── JwtAuthFilter ──
 * Filter đứng giữa request và controller, xử lý JWT token cho
 * MỖI request (OncePerRequestFilter đảm bảo chỉ chạy 1 lần/request).
 *
 * 📌 Filter là gì?
 *   Component nằm trong chuỗi xử lý của Servlet (FilterChain).
 *   Mỗi request đi qua lần lượt các filter trước khi đến controller.
 *   Spring Security cũng là 1 chuỗi filter.
 *
 * 📌 Luồng xử lý của filter này:
 *   [Request] → JwtAuthFilter → (các filter khác) → Controller
 *         │                                            │
 *         ├─ Token hợp lệ → set Authentication ────────┤
 *         └─ Token không có / sai → bỏ qua ────────────┤
 *                                                        → Nếu không có Authentication:
 *                                                           SecurityConfig sẽ chặn (401)
 *
 * 📌 @Component + @RequiredArgsConstructor:
 *   @Component: Spring tự động đăng ký filter này.
 *   @RequiredArgsConstructor: Lombok tự tạo constructor cho
 *     các final field (JwtUtil, UserRepository).
 *
 * 📌 OncePerRequestFilter:
 *   extends thay vì implements Filter để đảm bảo mỗi request
 *   chỉ chạy 1 lần (tránh trường hợp forward nội bộ chạy lại).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    /**
     * ── doFilterInternal() ──
     * Phương thức xử lý chính, được gọi cho mỗi request HTTP.
     *
     * Các bước:
     *   1. extractToken() → lấy token từ header "Authorization"
     *   2. Nếu có token:
     *      a. jwtUtil.validateToken() → kiểm tra chữ ký + hạn
     *      b. Lấy email từ token → tìm user trong DB
     *      c. Tạo CustomUserDetails từ user tìm được
     *      d. Tạo UsernamePasswordAuthenticationToken (auth object)
     *      e. Set auth vào SecurityContextHolder
     *   3. filterChain.doFilter() → chuyển request cho filter tiếp theo
     *
     * 📌 SecurityContextHolder:
     *   Nơi Spring Security lưu thông tin người dùng hiện tại.
     *   Giống như "session" nhưng ở mức request (stateless).
     *   Sau đó dùng @AuthenticationPrincipal để lấy trong controller.
     *
     * 📌 UsernamePasswordAuthenticationToken:
     *   Implement của Authentication interface.
     *   Tham số: (principal, credentials, authorities)
     *   - principal: UserDetails (thông tin user)
     *   - credentials: null (vì đã xác thực bằng JWT)
     *   - authorities: role của user
     *
     * 📌 WebAuthenticationDetailsSource:
     *   Lưu thêm thông tin request (IP, sessionId) vào auth object.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // ═══ Bước 1: Trích xuất token từ header ═══
        String token = extractToken(request);

        // ═══ Bước 2: Nếu có token hợp lệ, xác thực ═══
        if (token != null && jwtUtil.validateToken(token)) {
            // Lấy email từ token
            String email = jwtUtil.getEmailFromToken(token);

            // Tìm user trong DB
            User user = userRepository.findByEmail(email).orElse(null);

            // Nếu user tồn tại → tạo authentication object
            if (user != null) {
                CustomUserDetails userDetails = new CustomUserDetails(user);

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,          // principal
                                null,                 // credentials
                                userDetails.getAuthorities()  // role
                        );

                // Gắn thêm thông tin request vào auth
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // ═══ Bước 3: Set authentication vào SecurityContext ═══
                // Sau dòng này, controller có thể lấy user bằng
                // @AuthenticationPrincipal CustomUserDetails
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        // ═══ Bước 4: Chuyển request cho filter tiếp theo ═══
        // Luôn phải gọi dù có token hay không!
        filterChain.doFilter(request, response);
    }

    /**
     * ── extractToken() ──
     * Lấy JWT token từ header Authorization.
     *
     * Header mẫu: "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9..."
     *
     * StringUtils.hasText(): kiểm tra string không null, không rỗng,
     * không chỉ chứa whitespace (từ Spring Framework).
     *
     * bearer.substring(7): bỏ qua "Bearer " (7 ký tự) → lấy token.
     */
    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
