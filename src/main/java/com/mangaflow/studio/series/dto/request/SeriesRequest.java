package com.mangaflow.studio.series.dto.request;

import com.mangaflow.studio.series.enums.Genre;
import com.mangaflow.studio.series.enums.TargetDemographic;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * ── SeriesRequest DTO ──
 * DTO nhận dữ liệu từ client khi tạo hoặc cập nhật series.
 *
 * 📌 @Data (Lombok): Tự sinh getter, setter cho tất cả field.
 *
 * 📌 Validation:
 *    title → @NotBlank (bắt buộc, không được để trống)
 *    genre → @NotNull (bắt buộc)
 *    targetDemographic → @NotNull (bắt buộc)
 *    Các field còn lại → optional (nullable)
 *
 * 📌 Dùng trong:
 *    POST /api/series  → create
 *    PUT  /api/series/{id} → update
 *
 * 📌 Lưu ý với update:
 *    Client có thể gửi thiếu field → field đó sẽ không bị thay đổi
 *    (null-safe update trong SeriesService).
 */
@Data
public class SeriesRequest {

    /**
     * title: Tên series.
     * @NotBlank → không được null, không được rỗng, không được chỉ whitespace.
     * Đây là trường bắt buộc duy nhất khi tạo series.
     */
    @NotBlank(message = "Title is required")
    private String title;

    /**
     * titleJp: Tên tiếng Nhật (tuỳ chọn).
     * null → không cập nhật (khi update).
     */
    private String titleJp;

    /**
     * synopsis: Tóm tắt nội dung (tuỳ chọn).
     * Có thể là text dài.
     */
    private String synopsis;

    /**
     * genre: Thể loại (bắt buộc).
     * Client gửi dạng string → Jackson tự động parse sang Genre enum.
     * Nếu sai → GlobalExceptionHandler báo lỗi kèm danh sách enum hợp lệ.
     */
    @NotNull(message = "Genre is required")
    private Genre genre;

    /**
     * targetDemographic: Đối tượng độc giả (bắt buộc).
     */
    @NotNull(message = "Target demographic is required")
    private TargetDemographic targetDemographic;

    /**
     * coverColor: Màu nền cho card (tuỳ chọn).
     * VD: "#e63946" → frontend dùng làm fallback khi chưa có ảnh bìa.
     */
    private String coverColor;

    /**
     * coverImageUrl: Đường dẫn ảnh bìa (tuỳ chọn).
     * Hiện tại chưa có upload → có thể để null.
     */
    private String coverImageUrl;

    /**
     * isMature: Đánh dấu 18+ (tuỳ chọn).
     * Boolean (wrapper) → có thể null → Service xử lý null-safe.
     * Nếu null khi create → mặc định false (trong Entity).
     */
    private Boolean isMature;
}
