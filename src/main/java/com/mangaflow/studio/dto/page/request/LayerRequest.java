package com.mangaflow.studio.dto.page.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ── LayerRequest ──
 * DTO nhận dữ liệu từ client khi tạo hoặc cập nhật Layer.
 *
 * 📌 Dùng trong:
 *    - POST   /api/v1/pages/{pageId}/layers  — tạo layer mới
 *    - PUT    /api/v1/layers/{id}            — cập nhật layer
 *
 * ══════════════════════════════════════════════════════════════════
 *  Validation:
 * ══════════════════════════════════════════════════════════════════
 *  - label: bắt buộc khi tạo (validate trong service), optional khi update
 *  - Các field khác: nullable (optional) — nếu null thì giữ giá trị cũ
 */
@Data
@Schema(description = "Request tạo hoặc cập nhật Layer")
public class LayerRequest {

    @Size(max = 255, message = "Label must not exceed 255 characters")
    @Schema(
            description = "Tên hiển thị của layer (vd: 'Background - Tanaka')",
            example = "Background - Tanaka",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private String label;

    @Schema(
            description = "URL file ảnh của layer trên Cloudinary. " +
                    "Khi tạo từ task submission → lấy từ submission.resultImageUrl. " +
                    "Bỏ trống nếu layer không có ảnh (placeholder).",
            example = "https://res.cloudinary.com/.../result.jpg"
    )
    private String fileUrl;

    @Schema(
            description = "URL thumbnail của layer (hiển thị trong LayerPanel)",
            example = "https://res.cloudinary.com/.../thumb_result.jpg"
    )
    private String thumbnailUrl;

    @Schema(
            description = "Độ trong suốt (0.0 → 1.0). " +
                    "Mặc định 1.0 = 100% opacity (không trong suốt).",
            example = "0.75"
    )
    private Double opacity;

    @Schema(
            description = "Có hiển thị layer hay không. " +
                    "false = ẩn layer (mắt tắt trong LayerPanel).",
            example = "true"
    )
    private Boolean visible;

    @Schema(
            description = "Chế độ hoà trộn (blend mode). " +
                    "Các giá trị: normal, multiply, screen, overlay, darken, lighten... " +
                    "Mặc định: normal.",
            example = "normal"
    )
    private String blendMode;

    @Schema(
            description = "Khoá layer — không cho chỉnh sửa. " +
                    "false = có thể chỉnh sửa (rename, reorder, delete).",
            example = "false"
    )
    private Boolean locked;

    @Schema(
            description = "Thứ tự hiển thị (0 = dưới cùng). " +
                    "Dùng khi reorder layers.",
            example = "5"
    )
    private Integer sortOrder;
}
