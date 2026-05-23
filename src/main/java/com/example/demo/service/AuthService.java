package com.example.demo.service;

import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.dto.UpdateProfileRequest;
import com.example.demo.dto.UserDTO;
import com.example.demo.exception.AppException;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * ── AuthService ──
 * Service chứa logic nghiệp vụ xác thực: đăng ký và đăng nhập.
 *
 * 📌 @Service:
 *   Đánh dấu class là Service Layer (tầng nghiệp vụ).
 *   Spring sẽ tạo bean và inject vào Controller.
 *
 * 📌 @RequiredArgsConstructor (Lombok):
 *   Tự tạo constructor cho các final field (UserRepository, JwtUtil, PasswordEncoder).
 *   → Spring tự động inject dependency qua constructor injection.
 *
 * 📌 Luồng register:
 *   Client → Controller → AuthService.register() → UserRepository.save() → JWT → Response
 *
 * 📌 Luồng login:
 *   Client → Controller → AuthService.login() → UserRepository.findByEmail()
 *   → passwordEncoder.matches() → JWT → Response
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * ── register() ──
     * Đăng ký tài khoản mới.
     *
     * Các bước:
     *   1. Kiểm tra email đã tồn tại chưa → nếu rồi thì báo lỗi (400)
     *   2. Kiểm tra username đã tồn tại chưa → nếu rồi thì báo lỗi (400)
     *   3. Tạo entity User mới:
     *      - password: mã hoá bằng BCrypt trước khi lưu
     *      - role: nếu client không gửi → mặc định MANGAKA
     *      - displayName: nếu không gửi → lấy username
     *   4. Lưu user vào database
     *   5. Tạo JWT token
     *   6. Trả về AuthResponse (token + userDTO)
     *
     * 📌 PasswordEncoder.encode():
     *   Biến "password" → "$2a$10$XQ...m0O" (BCrypt hash)
     *   → Không bao giờ lưu plain text trong DB!
     *
     * 📌 Exception:
     *   Dùng RuntimeException (hoặc custom exception).
     *   Controller sẽ bắt và trả về 400 Bad Request.
     *   (Có thể dùng @ExceptionHandler trong Controller hoặc @ControllerAdvice)
     */
    public AuthResponse register(RegisterRequest request) {
        // ═══ Kiểm tra email trùng ═══
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(HttpStatus.CONFLICT, "Email already registered");
        }

        // ═══ Kiểm tra username trùng ═══
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AppException(HttpStatus.CONFLICT, "Username already taken");
        }

        // ═══ Tạo user mới ═══
        User user = User.builder()
                .email(request.getEmail())
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName() != null
                        ? request.getDisplayName()
                        : request.getUsername())
                .role(request.getRole() != null ? request.getRole() : Role.MANGAKA)
                .avatarUrl(null)
                .build();

        // ═══ Lưu xuống DB ═══
        User savedUser = userRepository.save(user);

        // ═══ Tạo JWT ═══
        String token = jwtUtil.generateToken(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getRole().name()
        );

        // ═══ Trả về response ═══
        return AuthResponse.builder()
                .accessToken(token)
                .user(UserDTO.fromEntity(savedUser))
                .build();
    }

    /**
     * ── login() ──
     * Đăng nhập bằng email + password.
     *
     * Các bước:
     *   1. Tìm user theo email → không thấy báo lỗi
     *   2. So sánh password (raw) với hash trong DB
     *      → passwordEncoder.matches(raw, hash) → true/false
     *   3. Sai password → báo lỗi
     *   4. Đúng → tạo JWT, trả về AuthResponse
     *
     * 📌 passwordEncoder.matches():
     *   Hash password client gửi lên rồi so sánh với hash trong DB.
     *   Không thể decode BCrypt — chỉ có thể "match" (so sánh hash).
     *
     * 📌 Lưu ý bảo mật:
     *   - Không nói "email không tồn tại" — nói "Invalid credentials" chung chung
     *     để tránh brute force dò email.
     *   - Ở đây tách riêng để dễ debug, production nên gộp chung.
     */
    public AuthResponse login(LoginRequest request) {
        // ═══ Tìm user theo email ═══
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        // ═══ Kiểm tra password ═══
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        // ═══ Tạo JWT ═══
        String token = jwtUtil.generateToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );

        // ═══ Trả về response ═══
        return AuthResponse.builder()
                .accessToken(token)
                .user(UserDTO.fromEntity(user))
                .build();
    }

    /**
     * ── updateProfile() ──
     * Cập nhật profile cho user (displayName, avatarUrl, bio).
     *
     * 📌 Tất cả field đều optional:
     *   Nếu request.getXxx() != null → update
     *   Nếu null → giữ nguyên giá trị cũ
     *
     * 📌 Các bước:
     *   1. Kiểm tra từng field trong request
     *   2. Nếu không null → set vào entity
     *   3. Save entity xuống DB
     *   4. Trả về UserDTO mới
     */
    public UserDTO updateProfile(User user, UpdateProfileRequest request) {
        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }
        return UserDTO.fromEntity(userRepository.save(user));
    }
}
