package com.mangaflow.studio.dto.comment.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * ── CommentRequest ──
 * DTO nhận dữ liệu từ Frontend khi tạo hoặc cập nhật Comment.
 *
 * 📌 Dùng trong:
 *    - POST   /api/v1/pages/{pageId}/comments  — tạo comment mới
 *    - POST   /api/v1/comments/{parentId}/replies — reply
 *    - PUT    /api/v1/comments/{id}            — cập nhật comment
 *
 * ══════════════════════════════════════════════════════════════════
 *  Các field:
 * ══════════════════════════════════════════════════════════════════
 *
 *  📍 content (bắt buộc):
 *     Nội dung comment — Frontend gửi text.
 *
 *  📍 parentId (tuỳ chọn):
 *     Nếu có → đây là reply của comment đó.
 *     Nếu null → đây là comment gốc (annotation).
 *
 *  📍 posX, posY (tuỳ chọn):
 *     Toạ độ của vùng đánh dấu trên ảnh page (pixel).
 *     Chỉ có ý nghĩa với comment gốc (annotation).
 *     Frontend dùng để vẽ ô vuông/ vòng tròn lên ảnh.
 *
 *  📍 posWidth, posHeight (tuỳ chọn):
 *     Kích thước vùng đánh dấu trên ảnh page.
 *
 * ══════════════════════════════════════════════════════════════════
 *  Validation:
 * ══════════════════════════════════════════════════════════════════
 *  - content: @NotBlank — không được để trống.
 *  - Các field khác: optional — có thể null.
 *
 * ══════════════════════════════════════════════════════════════════
 *  Ví dụ request body (tạo annotation):
 * ══════════════════════════════════════════════════════════════════
 *  {
 *      "content": "Sửa thoại nhân vật dòng 3",
 *      "posX": 150.0,
 *      "posY": 200.0,
 *      "posWidth": 100.0,
 *      "posHeight": 60.0
 *  }
 *
 *  => Tạo comment gốc (parentId = null) với toạ độ trên page.
 *
 * ══════════════════════════════════════════════════════════════════
 *  Ví dụ request body (reply):
 * ══════════════════════════════════════════════════════════════════
 *  {
 *      "content": "Đã sửa xong, check lại nhé"
 *  }
 *
 *  => Tạo reply cho 1 comment (parentId được gửi qua URL).
 */
@Data
@Schema(description = "Request tạo hoặc cập nhật Comment trên page")
public class CommentRequest {

    @NotBlank(message = "Content is required")
    @Schema(
            description = "Nội dung comment (không được để trống)",
            example = "Sửa thoại nhân vật dòng 3",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String content;

    @Schema(
            description = "ID của comment cha — null nếu là comment gốc (annotation), " +
                    "có giá trị nếu là reply trong thread",
            example = "1",
            nullable = true
    )
    private Long parentId;

    @Schema(
            description = "Toạ độ X của vùng đánh dấu trên ảnh page (pixel). " +
                    "Chỉ dùng cho comment gốc (annotation).",
            example = "150.0",
            nullable = true
    )
    private Double posX;

    @Schema(
            description = "Toạ độ Y của vùng đánh dấu trên ảnh page (pixel).",
            example = "200.0",
            nullable = true
    )
    private Double posY;

    @Schema(
            description = "Chiều rộng của vùng đánh dấu (pixel).",
            example = "100.0",
            nullable = true
    )
    private Double posWidth;

    @Schema(
            description = "Chiều cao của vùng đánh dấu (pixel).",
            example = "60.0",
            nullable = true
    )
    private Double posHeight;
}
