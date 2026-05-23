package com.example.demo.dto;

import com.example.demo.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * ── UserDTO ──
 * Phiên bản "an toàn" của entity User để trả về cho client.
 *
 * 📌 Tại sao không trả thẳng entity User?
 *   1. Password: entity có chứa password hash — không bao giờ được gửi ra ngoài.
 *   2. created_at: client không cần biết thời gian tạo tài khoản.
 *   3. Tách biệt: thay đổi entity không ảnh hưởng API response.
 *
 * 📌 @Builder:
 *   Cho phép tạo object kiểu:
 *     UserDTO.builder().id(1L).email("a@b.com").build()
 *
 * 📌 fromEntity(User):
 *   Static factory method — tiện lợi để convert Entity → DTO.
 *   Dùng trong AuthService/AuthController thay vì viết mapper riêng.
 */
@Data
@Builder
@AllArgsConstructor
public class UserDTO {

    private Long id;
    private String email;
    private String username;
    private String displayName;
    private String role;
    private String avatarUrl;
    private String bio;

    /**
     * Chuyển đổi entity User → UserDTO.
     *
     * Dùng @Builder để tạo object gọn gàng.
     * Chỉ lấy những field cần thiết, BỎ QUA password.
     */
    public static UserDTO fromEntity(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .role(user.getRole().name())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .build();
    }
}
