package com.mangaflow.studio.dto.task.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ── TaskSubmissionRequest ──
 * DTO nhận dữ liệu từ frontend khi ASSISTANT nộp bài.
 * <p>
 * 📌 Dùng ở:
 *    - POST /api/tasks/{taskId}/submissions (endpoint 9)
 * <p>
 * Chi tiết các field:
 *    - resultImageUrl: URL ảnh kết quả — bắt buộc
 *    - fileUrl:        URL file nguồn (PSD/CLIP) — không bắt buộc
 *    - note:           Ghi chú cho MANAGAKA — không bắt buộc
 */
@Data
@Schema(description = "Request nộp bài làm của ASSISTANT")
public class TaskSubmissionRequest {

    /**
     * resultImageUrl: URL ảnh kết quả (JPG/PNG).
     * Bắt buộc — ASSISTANT phải upload ảnh kết quả lên Cloudinary trước.
     * <p>
     * 📌 MANAGAKA xem ảnh này để đánh giá chất lượng bài làm.
     */
    @NotBlank(message = "Result image URL is required")
    @Size(max = 500, message = "Result image URL must not exceed 500 characters")
    @Schema(description = "URL ảnh kết quả (JPG/PNG)", example = "https://res.cloudinary.com/.../ket-qua.jpg", requiredMode = Schema.RequiredMode.REQUIRED)
    private String resultImageUrl;

    /**
     * fileUrl: URL file nguồn (PSD, CLIP, v.v.).
     * Không bắt buộc — ASSISTANT có thể gửi file gốc để MANAGAKA kiểm tra.
     */
    @Size(max = 500, message = "File URL must not exceed 500 characters")
    @Schema(description = "URL file nguồn (PSD/CLIP)", example = "https://res.cloudinary.com/.../file-hoan-chinh.psd")
    private String fileUrl;

    /**
     * note: Ghi chú cho MANAGAKA.
     * Không bắt buộc — ASSISTANT có thể ghi lời nhắn kèm bài nộp.
     */
    @Schema(description = "Ghi chú cho MANAGAKA", example = "Đã vẽ xong nhân vật chính, anh xem giúp em")
    private String note;
}
