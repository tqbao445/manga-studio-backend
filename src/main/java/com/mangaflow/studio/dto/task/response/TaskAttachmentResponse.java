package com.mangaflow.studio.dto.task.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ── TaskAttachmentResponse ──
 * DTO trả về thông tin 1 file đính kèm của task cho frontend.
 * <p>
 * 📌 Dùng ở:
 *    - POST  /api/tasks/{taskId}/attachments → file vừa đính kèm (endpoint 11)
 *    - GET   /api/tasks/{id}                → attachments trong task detail (endpoint 2)
 */
@Data
@Builder
@AllArgsConstructor
@Schema(description = "Thông tin file đính kèm của task")
public class TaskAttachmentResponse {

    @Schema(description = "ID của attachment", example = "1")
    private Long id;

    @Schema(description = "ID của task", example = "1")
    private Long taskId;

    @Schema(description = "URL file đính kèm", example = "https://...ref-asset.png")
    private String fileUrl;

    @Schema(description = "Tên file gốc", example = "mau-tham-khao.png")
    private String fileName;

    @Schema(description = "Thời điểm upload", example = "2026-05-30T10:30:00")
    private LocalDateTime uploadedAt;
}
