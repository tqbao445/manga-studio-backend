package com.mangaflow.studio.dto.series.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * ── InviteAssistantRequest ──
 * DTO nhận dữ liệu từ client khi MANGAKA mời assistant tham gia series.
 * <p>
 * 📌 Dùng trong:
 *    POST /api/series/{seriesId}/assistants/invite
 *    SeriesAssistantController.inviteAssistant()
 * <p>
 * 📌 Validation:
 *    assistantId → @NotNull (bắt buộc)
 *    Phải là ID của user có role ASSISTANT (validate ở Service layer)
 */
@Data
public class InviteAssistantRequest {

    /**
     * assistantId: ID của user (ASSISTANT) muốn mời vào series.
     * <p>
     * @NotNull: không được null — client bắt buộc gửi.
     * Service sẽ kiểm tra:
     *   - User có tồn tại không → nếu không → 404
     *   - User có role ASSISTANT không → nếu không → 400
     *   - Chưa có lời mời PENDING/ACCEPTED cho cặp (seriesId, assistantId) này
     */
    @NotNull(message = "Assistant ID is required")
    private Long assistantId;
}
