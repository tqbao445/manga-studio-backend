package com.example.demo.controller;

import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.dto.UpdateProfileRequest;
import com.example.demo.dto.UserDTO;
import com.example.demo.security.CustomUserDetails;
import com.example.demo.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * ── AuthController ──
 * REST Controller xử lý đăng ký, đăng nhập và lấy thông tin user.
 *
 * 📌 @RestController:
 *   @Controller + @ResponseBody — mọi method đều trả về JSON.
 *   Không cần @ResponseBody trên từng method.
 *
 * 📌 @RequestMapping("/api/auth"):
 *   Prefix tất cả endpoint trong class này bằng /api/auth.
 *   → register: POST /api/auth/register
 *   → login:    POST /api/auth/login
 *   → me:       GET  /api/auth/me
 *
 * 📌 @RequiredArgsConstructor (Lombok):
 *   Tự tạo constructor cho final field (AuthService).
 *
 * 📌 @Valid:
 *   Kích hoạt Jakarta Validation (các @NotBlank, @Email, @Size... trong DTO).
 *   Nếu không hợp lệ → tự động trả về 400 Bad Request với lỗi chi tiết.
 *
 * 📌 @AuthenticationPrincipal:
 *   Spring Security tự động inject CustomUserDetails (đã set trong JwtAuthFilter)
 *   khi request có token hợp lệ.
 *   Chỉ dùng trong endpoint /me — vì /register và /login không cần token.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * ── POST /api/auth/register ──
     * Đăng ký tài khoản mới.
     *
     * Request body (JSON):
     *   {
     *     "email": "newuser@manga.com",
     *     "username": "newuser",
     *     "password": "password123",
     *     "displayName": "New User",
     *     "role": "MANGAKA"
     *   }
     *
     * Response 201 (Created):
     *   {
     *     "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
     *     "user": { "id": 11, "email": "...", ... }
     *   }
     *
     * Response 400 (Bad Request) — validation lỗi hoặc email/username trùng:
     *   { "error": "Email already registered" }
     *
     * 📌 ResponseEntity.created():
     *   Trả về HTTP 201 Created (thay vì 200 OK) — chuẩn REST.
     *   .body() chứa dữ liệu trả về.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * ── POST /api/auth/login ──
     * Đăng nhập bằng email + password.
     *
     * Request body (JSON):
     *   {
     *     "email": "ichikawa@manga.com",
     *     "password": "password"
     *   }
     *
     * Response 200:
     *   {
     *     "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
     *     "user": { "id": 1, "email": "ichikawa@manga.com", ... }
     *   }
     *
     * Response 401 (Unauthorized) — sai email/password:
     *   { "error": "Invalid credentials" }
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * ── GET /api/auth/me ──
     * Lấy thông tin user hiện tại dựa vào JWT token.
     *
     * 📌 Yêu cầu:
     *   Header: Authorization: Bearer <token>
     *   (Nếu không có → SecurityConfig chặn lại, trả về 401)
     *
     * 📌 @AuthenticationPrincipal:
     *   Lấy CustomUserDetails từ SecurityContextHolder (đã set trong JwtAuthFilter).
     *   Chứa thông tin user + entity User gốc.
     *
     * 📌 Tại sao dùng @AuthenticationPrincipal thay vì @RequestHeader?
     *   - Không cần parse token thủ công.
     *   - Spring Security đã xác thực và có sẵn thông tin user.
     *   - An toàn hơn (không lộ secret key).
     *
     * Response 200:
     *   {
     *     "id": 1,
     *     "email": "ichikawa@manga.com",
     *     "username": "ichikawa",
     *     "displayName": "Ichikawa",
     *     "role": "MANGAKA",
     *     "avatarUrl": null
     *   }
     *
     * Response 401:
     *   Trả về mặc định của Spring Security (không cần xử lý).
     */
    @GetMapping("/me")
    public ResponseEntity<UserDTO> me(@AuthenticationPrincipal CustomUserDetails userDetails) {
        UserDTO userDTO = UserDTO.fromEntity(userDetails.getUser());
        return ResponseEntity.ok(userDTO);
    }

    /**
     * ── PATCH /api/auth/profile ──
     * Cập nhật profile: displayName, avatarUrl, bio.
     *
     * 📌 Request body (JSON) — tất cả field đều optional:
     *   {
     *     "displayName": "Ichikawa-sensei",
     *     "avatarUrl": "https://cloudinary.com/.../avatar.jpg",
     *     "bio": "Mangaka chuyên vẽ shonen, 5 năm kinh nghiệm"
     *   }
     *
     * 📌 Chỉ gửi field muốn update:
     *   { "displayName": "Tên mới" }              ✅ OK
     *   { "bio": "Bio mới" }                       ✅ OK
     *   { "displayName": "...", "avatarUrl": "..." } ✅ OK
     *
     * 📌 Luồng:
     *   Controller nhận request → gọi AuthService.updateProfile()
     *   → Service kiểm tra từng field null → set → save → trả UserDTO
     */
    @PatchMapping("/profile")
    public ResponseEntity<UserDTO> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody UpdateProfileRequest request) {
        UserDTO result = authService.updateProfile(userDetails.getUser(), request);
        return ResponseEntity.ok(result);
    }
}
