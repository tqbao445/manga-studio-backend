package com.mangaflow.studio.dto.task.request;

import com.mangaflow.studio.model.task.TaskSubmissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * ── SubmissionStatusRequest ──
 * DTO nhận dữ liệu từ frontend khi MANAGAKA duyệt bài nộp.
 * <p>
 * 📌 Dùng ở:
 *    - PATCH /api/submissions/{id}/status (endpoint 10)
 * <p>
 * Chỉ có 1 field: status (trạng thái duyệt).
 * <p>
 * Giá trị hợp lệ:
 *    - APPROVED:            Chấp nhận bài nộp → task DONE
 *    - REVISION_REQUIRED:   Yêu cầu sửa lại → task IN_PROGRESS
 */
@Data
@Schema(description = "Request duyệt bài nộp của MANAGAKA")
public class SubmissionStatusRequest {

    /**
     * status: Trạng thái duyệt mới.
     * Bắt buộc — chỉ chấp nhận APPROVED hoặc REVISION_REQUIRED.
     */
    @NotNull(message = "Status is required")
    @Schema(description = "Trạng thái duyệt: APPROVED hoặc REVISION_REQUIRED", example = "APPROVED", requiredMode = Schema.RequiredMode.REQUIRED)
    private TaskSubmissionStatus status;
}
