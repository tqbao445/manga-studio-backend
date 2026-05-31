package com.mangaflow.studio.dto.task.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * ── UserBasicDTO ──
 * DTO nhẹ chứa thông tin cơ bản của User (không chứa email, password, bio...).
 * Dùng để hiển thị thông tin assistant/assignedBy trong TaskResponse.
 * <p>
 * 📌 So với UserDTO (full):
 *    UserBasicDTO chỉ có 3 field: id, displayName, avatarUrl.
 *    UserDTO có đầy đủ: id, email, username, displayName, role, avatarUrl, bio.
 * <p>
 * 📌 Dùng ở TaskResponse.assistant và TaskResponse.assignedBy.
 */
@Data
@Builder
@AllArgsConstructor
@Schema(description = "Thông tin cơ bản của user (dùng trong TaskResponse)")
public class UserBasicDTO {

    /**
     * id: ID duy nhất của user.
     */
    @Schema(description = "ID của user", example = "5")
    private Long id;

    /**
     * displayName: Tên hiển thị của user.
     * VD: "Nguyễn Văn A"
     */
    @Schema(description = "Tên hiển thị", example = "Nguyễn Văn A")
    private String displayName;

    /**
     * avatarUrl: URL ảnh đại diện.
     * Có thể null nếu user chưa set avatar.
     */
    @Schema(description = "URL ảnh đại diện", example = "https://res.cloudinary.com/.../avatar.jpg")
    private String avatarUrl;
}
