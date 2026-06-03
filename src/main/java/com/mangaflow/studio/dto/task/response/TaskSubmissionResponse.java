package com.mangaflow.studio.dto.task.response;

import com.mangaflow.studio.model.task.TaskSubmissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ── TaskSubmissionResponse ──
 * DTO trả về thông tin 1 lần nộp bài (submission) cho frontend.
 * <p>
 * 📌 Dùng ở:
 *    - GET  /api/tasks/{taskId}/submissions → lịch sử nộp bài (endpoint 8)
 *    - POST /api/tasks/{taskId}/submissions → bài vừa nộp (endpoint 9)
 *    - PATCH /api/submissions/{id}/status   → bài sau duyệt (endpoint 10)
 *    - GET  /api/tasks/{id}                 → submissions trong task detail (endpoint 2)
 * <p>
 * 📌 Sắp xếp theo version giảm dần (mới nhất trước).
 */
@Data
@Builder
@AllArgsConstructor
@Schema(description = "Thông tin 1 lần nộp bài (submission)")
public class TaskSubmissionResponse {

    @Schema(description = "ID của submission", example = "1")
    private Long id;

    @Schema(description = "ID của task", example = "1")
    private Long taskId;

    @Schema(description = "URL ảnh kết quả", example = "https://...submit-v2.jpg")
    private String resultImageUrl;

    @Schema(description = "URL thumbnail của ảnh kết quả (resize w_320)", example = "https://...c_limit,w_320/.../submit-v2.jpg")
    private String thumbnailUrl;

    @Schema(description = "URL file nguồn (PSD/CLIP)", example = "https://...file-v2.psd")
    private String fileUrl;

    @Schema(description = "Ghi chú của ASSISTANT", example = "Đã sửa theo yêu cầu")
    private String note;

    @Schema(description = "Ghi chú của MANGAKA khi duyệt", example = "Cần sửa màu sắc và thêm shadow")
    private String reviewNote;

    @Schema(description = "Số phiên bản (1, 2, 3...)", example = "2")
    private Integer version;

    @Schema(description = "Trạng thái: SUBMITTED, APPROVED, REVISION_REQUIRED", example = "SUBMITTED")
    private TaskSubmissionStatus status;

    @Schema(description = "Thời điểm nộp", example = "2026-06-02T10:00:00")
    private LocalDateTime submittedAt;
}
