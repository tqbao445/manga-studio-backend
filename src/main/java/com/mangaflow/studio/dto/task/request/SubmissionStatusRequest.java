package com.mangaflow.studio.dto.task.request;

import com.mangaflow.studio.model.task.TaskSubmissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * ── SubmissionStatusRequest ──
 * DTO nhận dữ liệu từ frontend khi MANGAKA duyệt bài nộp.
 * <p>
 * 📌 Dùng ở:
 *    - PATCH /api/submissions/{id}/status (endpoint 10)
 * <p>
 * Giá trị hợp lệ:
 *    - APPROVED:            Chấp nhận bài nộp → task DONE
 *    - REVISION_REQUIRED:   Yêu cầu sửa lại → task IN_PROGRESS
 */
@Data
@Schema(description = "Request duyệt bài nộp của MANGAKA")
public class SubmissionStatusRequest {

    /**
     * status: Trạng thái duyệt mới.
     * Bắt buộc — chỉ chấp nhận APPROVED hoặc REVISION_REQUIRED.
     */
    @NotNull(message = "Status is required")
    @Schema(description = "Trạng thái duyệt: APPROVED hoặc REVISION_REQUIRED", example = "APPROVED", requiredMode = Schema.RequiredMode.REQUIRED)
    private TaskSubmissionStatus status;

    /**
     * note: Ghi chú của MANGAKA khi duyệt.
     * Không bắt buộc — lưu vào submission để ASSISTANT biết lý do.
     */
    @Schema(description = "Ghi chú của MANGAKA khi duyệt", example = "Cần sửa màu sắc và thêm shadow")
    private String note;
}
