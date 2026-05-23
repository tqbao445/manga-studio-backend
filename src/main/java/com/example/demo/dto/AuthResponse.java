package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * ── AuthResponse DTO ──
 * Response trả về khi login hoặc register thành công.
 *
 * Gồm 2 phần:
 *   1. accessToken: JWT string — client lưu lại, gửi kèm mọi request
 *   2. user: UserDTO — thông tin user để frontend hiển thị, lưu store
 *
 * 📌 Phản hồi mẫu:
 *   {
 *     "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
 *     "user": {
 *       "id": 1,
 *       "email": "ichikawa@manga.com",
 *       "username": "ichikawa",
 *       "displayName": "Ichikawa",
 *       "role": "MANGAKA",
 *       "avatarUrl": null
 *     }
 *   }
 */
@Data
@Builder
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private UserDTO user;
}
