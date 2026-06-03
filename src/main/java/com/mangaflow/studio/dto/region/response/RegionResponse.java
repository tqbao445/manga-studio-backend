package com.mangaflow.studio.dto.region.response;

import com.mangaflow.studio.model.region.RegionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ── RegionResponse ──
 * DTO trả về thông tin region cho frontend.
 * <p>
 * 📌 Dùng ở tất cả các endpoint Region:
 *    - GET    /api/v1/pages/{pageId}/regions          → list regions
 *    - POST   /api/v1/pages/{pageId}/regions          → tạo mới
 *    - PUT    /api/v1/regions/{id}                    → cập nhật
 *    - PATCH  /api/v1/regions/{id}/status             → đổi status
 *    - PUT    /api/v1/pages/{pageId}/regions/reorder  → sắp xếp
 * <p>
 * 📌 @Builder: Cho phép tạo object kiểu:
 *    RegionResponse.builder().id(1L).regionType(CHARACTER).build()
 * <p>
 * 📌 @AllArgsConstructor: Cần cho @Builder hoạt động.
 */
@Data
@Builder
@AllArgsConstructor
@Schema(description = "Thông tin chi tiết của 1 region (vùng vẽ trên page)")
public class RegionResponse {

    /**
     * id: ID duy nhất của region.
     * Do database tự sinh (IDENTITY).
     */
    @Schema(description = "ID của region", example = "1")
    private Long id;

    /**
     * pageId: ID của page chứa region này.
     * Dùng để frontend biết region thuộc page nào.
     */
    @Schema(description = "ID của page chứa region", example = "1")
    private Long pageId;

    /**
     * regionType: Loại vùng.
     * Trả về tên enum dạng chữ (VD: "CHARACTER").
     */
    @Schema(description = "Loại vùng: BACKGROUND, CHARACTER, TEXT, EFFECT, TONE, OTHER", example = "CHARACTER")
    private RegionType regionType;

    /**
     * label: Tên hiển thị của region.
     * Có thể null nếu người dùng chưa đặt tên.
     */
    @Schema(description = "Tên hiển thị của region", example = "Nhân vật chính panel 3")
    private String label;

    /**
     * x: Toạ độ X góc trên trái (pixel).
     */
    @Schema(description = "Toạ độ X góc trên trái (pixel)", example = "500")
    private Integer x;

    /**
     * y: Toạ độ Y góc trên trái (pixel).
     */
    @Schema(description = "Toạ độ Y góc trên trái (pixel)", example = "800")
    private Integer y;

    /**
     * width: Chiều rộng vùng (pixel).
     */
    @Schema(description = "Chiều rộng vùng (pixel)", example = "800")
    private Integer width;

    /**
     * height: Chiều cao vùng (pixel).
     */
    @Schema(description = "Chiều cao vùng (pixel)", example = "1200")
    private Integer height;

    /**
     * color: Màu hiển thị trên canvas (hex).
     * Frontend dùng màu này để vẽ khung viền region.
     */
    @Schema(description = "Màu hiển thị trên canvas (hex)", example = "#FF6B6B")
    private String color;

    /**
     * sortOrder: Thứ tự render (0 = dưới cùng).
     * Số càng nhỏ → vẽ trước (nằm dưới).
     * Số càng lớn → vẽ sau (nằm trên).
     * <p>
     * 📌 Dùng để sắp xếp regions khi render canvas.
     *    Frontend sort theo sortOrder ASC.
     */
    @Schema(description = "Thứ tự render (0 = dưới cùng)", example = "0")
    private Integer sortOrder;

    /**
     * createdAt: Thời điểm tạo region.
     * Định dạng ISO datetime (VD: "2026-05-30T10:00:00").
     */
    @Schema(description = "Thời điểm tạo region", example = "2026-05-30T10:00:00")
    private LocalDateTime createdAt;
}
