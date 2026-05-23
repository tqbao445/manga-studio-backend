package com.example.demo.dto;

import com.example.demo.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ── RegisterRequest DTO ──
 * DTO (Data Transfer Object): object chuyên chở dữ liệu giữa client và server.
 *
 * 📌 DTO vs Entity:
 *   - Entity (User): ánh xạ tới DB, có @Entity, chứa password hash, createdAt...
 *   - DTO (RegisterRequest): chỉ chứa dữ liệu client gửi lên, có validation
 *   → Tách biệt để không lộ cấu trúc DB ra ngoài.
 *
 * 📌 @Data (Lombok): tự sinh getter, setter, toString.
 *
 * 📌 Jakarta Validation (@NotBlank, @Email, @Size):
 *   @Valid trong controller sẽ tự động validate các annotation này.
 *   Nếu không hợp lệ → ném MethodArgumentNotValidException → 400 Bad Request.
 *
 * 📌 @NotBlank: Khác với @NotNull (chỉ check null) và @NotEmpty (check null + rỗng).
 *   @NotBlank = null + rỗng + chỉ whitespace → đều fail.
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3-50 characters")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    private String displayName;

    /**
     * Vai trò mặc định là MANGAKA.
     * Client có thể gửi role khác nếu muốn.
     */
    private Role role;
}
