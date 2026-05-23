package com.example.demo.security;

import com.example.demo.model.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * ── CustomUserDetails ──
 * Adapter pattern: Chuyển đổi entity User (của chúng ta) thành
 * UserDetails (interface của Spring Security) để SecurityContext
 * có thể hiểu và sử dụng.
 *
 * 📌 UserDetails là gì?
 *   Interface của Spring Security chứa thông tin người dùng hiện tại:
 *     - getUsername() → email (dùng làm định danh)
 *     - getPassword() → password đã hash
 *     - getAuthorities() → danh sách quyền (ROLE_...)
 *     - isAccountNonExpired/Locked/Enabled → trạng thái tài khoản
 *
 * 📌 @Getter (Lombok):
 *   Tự sinh getter cho tất cả field (bao gồm cả field `user`).
 *
 * 📌 getAuthorities():
 *   Trả về danh sách GrantedAuthority (quyền).
 *   SimpleGrantedAuthority = 1 quyền đơn lẻ.
 *   Trong Spring Security, quyền mặc định cho role có dạng "ROLE_<TÊN>".
 *   Ví dụ: "ROLE_MANGAKA", "ROLE_ASSISTANT"
 *   → Dùng trong SecurityConfig với hasRole("MANGAKA") tự động thêm "ROLE_".
 *
 * 📌 Tại sao cần class này?
 *   JwtAuthFilter cần tạo Authentication object để set vào
 *   SecurityContext. Authentication cần UserDetails → class này
 *   làm cầu nối giữa entity User (từ DB) và UserDetails (Spring).
 */
@Getter
public class CustomUserDetails implements UserDetails {

    /**
     * Giữ tham chiếu tới entity User gốc để có thể lấy thêm
     * thông tin (userId, displayName, role string) khi cần.
     */
    private final User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    /**
     * Quyền của user: 1 user có thể có nhiều quyền.
     * Ở đây mỗi user chỉ có 1 role → 1 authority.
     * Format: "ROLE_MANGAKA", "ROLE_TANTOU_EDITOR",...
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    /** Spring Security dùng để xác thực password. */
    @Override
    public String getPassword() {
        return user.getPassword();
    }

    /**
     * Dùng email làm username để đăng nhập.
     * Có thể đổi thành username nếu muốn login bằng username.
     */
    @Override
    public String getUsername() {
        return user.getEmail();
    }

    // ── Các trạng thái tài khoản ──
    // Nếu muốn khoá user, set trường này trong entity và trả về false.
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }

    // ── Các getter bổ sung (ngoài UserDetails interface) ──
    // @Getter đã tự sinh, chỉ cần viết lại để comment rõ mục đích.

    public Long getUserId() {
        return user.getId();
    }

    public String getDisplayName() {
        return user.getDisplayName();
    }

    public String getRole() {
        return user.getRole().name();
    }
}
