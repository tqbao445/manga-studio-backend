package com.mangaflow.studio.dto.series.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * ── InviteTantouRequest ──
 * DTO nhận dữ liệu từ client khi MANGAKA mời tantou tham gia kiểm duyệt series.
 * <p>
 * 📌 Dùng trong:
 *    POST /api/series/{seriesId}/tantou/invite
 *    TantouInvitationController.inviteTantou()
 * <p>
 * 📌 Validation:
 *    tantouId → @NotNull (bắt buộc)
 *    Phải là ID của user có role TANTOU_EDITOR (validate ở Service layer)
 */
@Data
public class InviteTantouRequest {

    /**
     * tantouId: ID của user (TANTOU_EDITOR) muốn mời vào duyệt series.
     * <p>
     * @NotNull: không được null — client bắt buộc gửi.
     * Service sẽ kiểm tra:
     *   - User có tồn tại không → nếu không → 404
     *   - User có role TANTOU_EDITOR không → nếu không → 400
     *   - Chưa có lời mời PENDING/ACCEPTED cho cặp (seriesId, tantouId) này
     */
    @NotNull(message = "Tantou ID is required")
    private Long tantouId;
}
