package com.example.demo.dto;

import lombok.Data;

/**
 * ── UpdateProfileRequest DTO ──
 * Dùng cho request PATCH /api/auth/profile.
 *
 * 📌 Tất cả field đều optional (có thể null):
 *   User chỉ muốn sửa 1 field vẫn gửi được.
 *   Service sẽ kiểm tra null và chỉ update field không null.
 */
@Data
public class UpdateProfileRequest {
    private String displayName;
    private String avatarUrl;
    private String bio;
}
