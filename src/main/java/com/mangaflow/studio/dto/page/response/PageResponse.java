package com.mangaflow.studio.dto.page.response;

import com.mangaflow.studio.model.page.PageStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@Schema(description = "Thông tin chi tiết của 1 page (trang truyện)")
public class PageResponse {

    @Schema(description = "ID của page", example = "1")
    private Long id;

    @Schema(description = "ID của chapter chứa page này", example = "1")
    private Long chapterId;

    @Schema(description = "Số thứ tự page trong chapter", example = "1")
    private Integer pageNumber;

    @Schema(description = "URL ảnh gốc full size (dùng trong workspace)", example = "https://res.cloudinary.com/dklp7kcl9/image/upload/v1234567/manga_studio/u3/s1/ch5/p1.jpg")
    private String originalImageUrl;

    @Schema(description = "URL ảnh resize 1920px (hiển thị trên web)", example = "https://res.cloudinary.com/dklp7kcl9/image/upload/c_limit,w_1920/v1234567/manga_studio/u3/s1/ch5/p1.jpg")
    private String webImageUrl;

    @Schema(description = "URL ảnh thumbnail 320px (danh sách pages)")
    private String thumbnailUrl;

    @Schema(description = "Public ID trên Cloudinary (dùng để xoá ảnh)", example = "manga_studio/u3/s1/ch5/p1")
    private String publicId;

    @Schema(description = "Chiều rộng ảnh gốc (px)", example = "4200")
    private Integer width;

    @Schema(description = "Chiều cao ảnh gốc (px)", example = "6000")
    private Integer height;

    @Schema(description = "Trạng thái page: UPLOADED, REGIONS_DEFINED, IN_PRODUCTION, COMPLETED", example = "UPLOADED")
    private PageStatus status;

    @Schema(description = "Thời điểm tạo page", example = "2026-05-29T10:00:00")
    private LocalDateTime createdAt;
}
