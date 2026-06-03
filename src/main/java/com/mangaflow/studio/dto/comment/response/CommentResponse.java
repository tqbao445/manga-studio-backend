package com.mangaflow.studio.dto.comment.response;

import com.mangaflow.studio.model.comment.CommentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ── CommentResponse ──
 * DTO trả về cho Frontend khi client gọi API liên quan đến Comment.
 *
 * 📌 @Data: Lombok — tự sinh getter, setter, toString, equals, hashCode.
 * 📌 @Builder: Pattern Builder — tạo object kiểu:
 *    CommentResponse.builder().id(1L).content("...").build()
 * 📌 @AllArgsConstructor: Cần thiết cho @Builder hoạt động.
 *
 * ══════════════════════════════════════════════════════════════════
 *  Các field trả về:
 * ══════════════════════════════════════════════════════════════════
 *
 *  📍 id:          ID comment (tự động tạo).
 *  📍 content:     Nội dung text.
 *  📍 authorId:    ID người viết (để Frontend kiểm tra "có phải tôi không?").
 *  📍 authorName:  Tên hiển thị — Frontend show tên.
 *  📍 authorAvatar: Avatar người viết.
 *  📍 parentId:    Nếu null → comment gốc; có số → reply của comment đó.
 *  📍 pageId:      Comment thuộc page nào.
 *  📍 status:      ACTIVE / RESOLVED.
 *  📍 posX..posHeight: Toạ độ annotation (null với reply).
 *  📍 createdAt:   Thời gian tạo.
 *  📍 updatedAt:   Thời gian sửa gần nhất.
 *  📍 replies:     Danh sách reply (thread) — chỉ có ở comment gốC.
 *
 * ══════════════════════════════════════════════════════════════════
 *  Ví dụ JSON trả về:
 * ══════════════════════════════════════════════════════════════════
 *  {
 *      "id": 1,
 *      "content": "Thoại sai dòng 3",
 *      "authorId": 2,
 *      "authorName": "Tantou Editor A",
 *      "authorAvatar": "https://...",
 *      "parentId": null,
 *      "pageId": 10,
 *      "status": "ACTIVE",
 *      "posX": 150.0,
 *      "posY": 200.0,
 *      "posWidth": 100.0,
 *      "posHeight": 60.0,
 *      "createdAt": "2026-06-03T10:00:00",
 *      "updatedAt": "2026-06-03T10:00:00",
 *      "replies": [
 *          {
 *              "id": 2,
 *              "content": "Đã sửa rồi",
 *              "authorId": 1,
 *              "authorName": "Mangaka01",
 *              "parentId": 1,
 *              "pageId": 10,
 *              "status": "ACTIVE",
 *              "posX": null,
 *              "replies": null,
 *              ...
 *          }
 *      ]
 *  }
 */
@Data
@Builder
@AllArgsConstructor
@Schema(description = "Thông tin chi tiết của 1 Comment (bao gồm replies)")
public class CommentResponse {

    @Schema(description = "ID của comment", example = "1")
    private Long id;

    @Schema(description = "Nội dung comment", example = "Sửa thoại nhân vật dòng 3")
    private String content;

    @Schema(description = "ID của người viết comment", example = "2")
    private Long authorId;

    @Schema(description = "Tên hiển thị của người viết", example = "Tantou Editor A")
    private String authorName;

    @Schema(description = "URL avatar của người viết", example = "https://res.cloudinary.com/.../avatar.jpg")
    private String authorAvatar;

    @Schema(
            description = "ID của comment cha. Null = comment gốc (annotation). " +
                    "Có giá trị = đây là reply trong thread.",
            example = "1",
            nullable = true
    )
    private Long parentId;

    @Schema(description = "ID của page chứa comment này", example = "10")
    private Long pageId;

    @Schema(description = "Trạng thái: ACTIVE (đang hoạt động) hoặc RESOLVED (đã giải quyết)", example = "ACTIVE")
    private CommentStatus status;

    @Schema(description = "Toạ độ X trên ảnh page (pixel)", example = "150.0")
    private Double posX;

    @Schema(description = "Toạ độ Y trên ảnh page (pixel)", example = "200.0")
    private Double posY;

    @Schema(description = "Chiều rộng vùng đánh dấu (pixel)", example = "100.0")
    private Double posWidth;

    @Schema(description = "Chiều cao vùng đánh dấu (pixel)", example = "60.0")
    private Double posHeight;

    @Schema(description = "Thời điểm tạo comment", example = "2026-06-03T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Thời điểm cập nhật gần nhất", example = "2026-06-03T10:30:00")
    private LocalDateTime updatedAt;

    @Schema(
            description = "Danh sách reply (thread) — chỉ có ở comment gốc. " +
                    "Mỗi reply là 1 CommentResponse (không có replies lồng thêm).",
            nullable = true
    )
    private List<CommentResponse> replies;
}
