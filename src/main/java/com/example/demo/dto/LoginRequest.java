package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * ── LoginRequest DTO ──
 * Dùng cho request POST /api/auth/login.
 *
 * Chỉ gồm email + password — đơn giản nhất có thể.
 * Validation: cả 2 đều required, email phải đúng định dạng.
 */
@Data
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
