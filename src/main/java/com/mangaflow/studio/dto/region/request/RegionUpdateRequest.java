package com.mangaflow.studio.dto.region.request;

import com.mangaflow.studio.model.region.RegionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request cập nhật region — tất cả field đều optional (partial update)")
public class RegionUpdateRequest {

    @Schema(description = "Loại vùng: BACKGROUND, CHARACTER, TEXT, EFFECT, TONE, OTHER", example = "CHARACTER")
    private RegionType regionType;

    @Schema(description = "Tên hiển thị của region", example = "Nhân vật chính panel 3")
    private String label;

    @Schema(description = "Toạ độ X góc trên trái (pixel)", example = "500")
    private Integer x;

    @Schema(description = "Toạ độ Y góc trên trái (pixel)", example = "800")
    private Integer y;

    @Schema(description = "Chiều rộng vùng (pixel)", example = "800")
    private Integer width;

    @Schema(description = "Chiều cao vùng (pixel)", example = "1200")
    private Integer height;

    @Schema(description = "Màu hiển thị trên canvas (hex). Nếu không gửi, backend tự gán theo regionType", example = "#FF6B6B")
    private String color;
}
