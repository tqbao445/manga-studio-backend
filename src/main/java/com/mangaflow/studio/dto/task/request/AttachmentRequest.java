package com.mangaflow.studio.dto.task.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ── AttachmentRequest ──
 * DTO nhận dữ liệu từ frontend khi MANGAKA đính kèm file tham khảo.
 * <p>
 * 📌 Dùng ở:
 *    - POST /api/tasks/{taskId}/attachments (endpoint 11)
 * <p>
 * Chỉ có 1 field: fileUrl (URL của file đính kèm).
 * Validation: fileUrl bắt buộc, max 500 ký tự.
 */
@Data
@Schema(description = "Request đính kèm file tham khảo cho task")
public class AttachmentRequest {

    /**
     * fileUrl: URL của file đính kèm (ảnh mẫu, tài liệu, ...).
     * Bắt buộc — MANGAKA phải upload file lên Cloudinary trước.
     */
    @NotBlank(message = "File URL is required")
    @Size(max = 500, message = "File URL must not exceed 500 characters")
    @Schema(description = "URL file đính kèm", example = "https://res.cloudinary.com/.../tham-khao.jpg", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fileUrl;
}
