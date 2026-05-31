package com.mangaflow.studio.dto.region.request;

import com.mangaflow.studio.model.region.RegionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * ── RegionRequest ──
 * DTO nhận dữ liệu từ frontend khi tạo mới hoặc cập nhật region.
 * <p>
 * 📌 Dùng ở:
 *    - POST /api/v1/pages/{pageId}/regions → tạo region mới
 *    - PUT  /api/v1/regions/{id}           → cập nhật region
 * <p>
 * 📌 Validation:
 *    - regionType: bắt buộc, phải là 1 trong 6 loại (BACKGROUND, CHARACTER...)
 *    - x, y: bắt buộc, >= 0
 *    - width, height: bắt buộc, > 0
 *    - label: không bắt buộc
 *    - color: không bắt buộc, backend tự gán nếu null
 * <p>
 * 📌 Khi update (PUT), tất cả field đều optional trong service,
 *    nhưng request DTO này vẫn để @NotNull cho create.
 *    Service sẽ xử lý update bằng cách chỉ gán field không null.
 */
@Data
@Schema(description = "Request tạo hoặc cập nhật region (vùng vẽ trên page)")
public class RegionRequest {

    /**
     * regionType: Loại vùng (BACKGROUND, CHARACTER, TEXT...).
     * Bắt buộc — region nào cũng phải có loại.
     * <p>
     * 📌 @Schema(example = "CHARACTER"):
     *    Hiển thị ví dụ trong Swagger UI.
     */
    @NotNull(message = "Region type is required")
    @Schema(description = "Loại vùng: BACKGROUND, CHARACTER, TEXT, EFFECT, TONE, OTHER", example = "CHARACTER")
    private RegionType regionType;

    /**
     * label: Tên hiển thị của region (VD: "Nhân vật chính panel 3").
     * Không bắt buộc — có thể để trống, frontend sẽ hiển thị regionType.
     * <p>
     * 📌 Hiển thị trong RegionPanel của workspace.
     *    Người dùng có thể click để sửa label sau khi tạo.
     */
    @Schema(description = "Tên hiển thị của region", example = "Nhân vật chính panel 3")
    private String label;

    /**
     * x: Toạ độ X góc trên trái (pixel).
     * Bắt buộc — phải >= 0.
     * <p>
     * 📌 Tính từ mép trái ảnh page gốc.
     *    VD: x = 500 → region cách mép trái 500px.
     */
    @NotNull(message = "X coordinate is required")
    @Min(value = 0, message = "X must be >= 0")
    @Schema(description = "Toạ độ X góc trên trái (pixel)", example = "500")
    private Integer x;

    /**
     * y: Toạ độ Y góc trên trái (pixel).
     * Bắt buộc — phải >= 0.
     * <p>
     * 📌 Tính từ mép trên ảnh page gốc.
     *    VD: y = 800 → region cách mép trên 800px.
     */
    @NotNull(message = "Y coordinate is required")
    @Min(value = 0, message = "Y must be >= 0")
    @Schema(description = "Toạ độ Y góc trên trái (pixel)", example = "800")
    private Integer y;

    /**
     * width: Chiều rộng của region (pixel).
     * Bắt buộc — phải > 0.
     * <p>
     * 📌 Tính từ toạ độ x sang bên phải.
     *    VD: x=500, width=800 → region từ pixel 500 đến 1300.
     */
    @NotNull(message = "Width is required")
    @Min(value = 1, message = "Width must be > 0")
    @Schema(description = "Chiều rộng vùng (pixel)", example = "800")
    private Integer width;

    /**
     * height: Chiều cao của region (pixel).
     * Bắt buộc — phải > 0.
     * <p>
     * 📌 Tính từ toạ độ y xuống dưới.
     *    VD: y=800, height=1200 → region từ pixel 800 đến 2000.
     */
    @NotNull(message = "Height is required")
    @Min(value = 1, message = "Height must be > 0")
    @Schema(description = "Chiều cao vùng (pixel)", example = "1200")
    private Integer height;

    /**
     * color: Màu hiển thị của region trên canvas (hex, VD: "#FF6B6B").
     * Không bắt buộc — nếu không gửi, backend tự gán màu mặc định theo regionType:
     * <p>
     *    - BACKGROUND → #4ECDC4 (xanh ngọc)
     *    - CHARACTER  → #FF6B6B (đỏ)
     *    - TEXT       → #FFE66D (vàng)
     *    - EFFECT     → #A78BFA (tím)
     *    - TONE       → #6B7280 (xám)
     *    - OTHER      → #6B7280 (xám)
     */
    @Schema(description = "Màu hiển thị trên canvas (hex). Nếu không gửi, backend tự gán theo regionType", example = "#FF6B6B")
    private String color;
}
