package com.mangaflow.studio.dto.task.response;

import com.mangaflow.studio.model.region.RegionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
@Schema(description = "Thông tin cơ bản của region trong task response")
public class RegionBasicDTO {

    private Long id;

    @Schema(description = "Loại vùng", example = "CHARACTER")
    private RegionType regionType;

    @Schema(description = "Tên hiển thị", example = "Nhân vật chính")
    private String label;

    @Schema(description = "Toạ độ X", example = "120")
    private Integer x;

    @Schema(description = "Toạ độ Y", example = "340")
    private Integer y;

    @Schema(description = "Chiều rộng", example = "200")
    private Integer width;

    @Schema(description = "Chiều cao", example = "150")
    private Integer height;

    @Schema(description = "Màu sắc", example = "#FF6B6B")
    private String color;
}
