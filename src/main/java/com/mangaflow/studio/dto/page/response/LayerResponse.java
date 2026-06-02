package com.mangaflow.studio.dto.page.response;

import com.mangaflow.studio.dto.task.response.UserBasicDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ── LayerResponse ──
 * DTO trả về thông tin layer cho frontend.
 * <p>
 * 📌 Dùng trong:
 *    - GET    /api/v1/pages/{pageId}/layers     — danh sách layer
 *    - GET    /api/v1/layers/{id}               — chi tiết 1 layer
 *    - POST   /api/v1/pages/{pageId}/layers     — tạo mới
 *    - PUT    /api/v1/layers/{id}               — cập nhật
 *    - (ẩn)  TaskService reviewSubmission approve → trả về LayerResponse
 * <p>
 * ══════════════════════════════════════════════════════════════════
 *  Mapper: LayerMapper (MapStruct) ✨
 * ══════════════════════════════════════════════════════════════════
 *  Layer → LayerResponse
 *  LayerRequest + Layer → Layer (merge)
 */
@Data
@Builder
@AllArgsConstructor
@Schema(description = "Thông tin chi tiết của 1 Layer")
public class LayerResponse {

    @Schema(description = "ID của layer", example = "1")
    private Long id;

    @Schema(description = "ID của page chứa layer này", example = "10")
    private Long pageId;

    @Schema(description = "Tên hiển thị trong LayerPanel", example = "Background - Tanaka")
    private String label;

    @Schema(description = "URL ảnh full size trên Cloudinary", example = "https://res.cloudinary.com/.../result.jpg")
    private String fileUrl;

    @Schema(description = "URL thumbnail (icon nhỏ trong LayerPanel)", example = "https://res.cloudinary.com/.../thumb_result.jpg")
    private String thumbnailUrl;

    @Schema(description = "Thứ tự hiển thị (0 = dưới cùng)", example = "1")
    private Integer sortOrder;

    @Schema(description = "Độ trong suốt (0.0 → 1.0)", example = "1.0")
    private Double opacity;

    @Schema(description = "Có hiển thị layer không", example = "true")
    private Boolean visible;

    @Schema(description = "Chế độ hoà trộn (normal, multiply, screen...)", example = "normal")
    private String blendMode;

    @Schema(description = "Khoá layer (không cho chỉnh sửa)", example = "false")
    private Boolean locked;

    @Schema(description = "Thông tin người tạo layer")
    private UserBasicDTO createdBy;

    @Schema(description = "Thời điểm tạo layer", example = "2026-05-29T10:00:00")
    private LocalDateTime createdAt;
}
