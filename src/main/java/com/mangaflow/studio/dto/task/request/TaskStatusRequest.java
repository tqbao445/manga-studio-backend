package com.mangaflow.studio.dto.task.request;

import com.mangaflow.studio.model.task.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * ── TaskStatusRequest ──
 * DTO nhận dữ liệu từ frontend khi chuyển trạng thái task.
 * <p>
 * 📌 Dùng ở:
 *    - PATCH /api/tasks/{id}/status (endpoint 6)
 * <p>
 * Chỉ có 1 field: status (trạng thái mới).
 * Validation: status không được null.
 */
@Data
@Schema(description = "Request chuyển trạng thái task")
public class TaskStatusRequest {

    /**
     * status: Trạng thái mới của task.
     * Bắt buộc — không được null.
     * <p>
     * Giá trị hợp lệ (tuỳ role và trạng thái hiện tại):
     *    - ASSISTANT: TODO → IN_PROGRESS, REJECTED → IN_PROGRESS
     *    - MANAGAKA:  IN_PROGRESS → REJECTED
     */
    @NotNull(message = "Status is required")
    @Schema(description = "Trạng thái mới", example = "IN_PROGRESS", requiredMode = Schema.RequiredMode.REQUIRED)
    private TaskStatus status;
}
