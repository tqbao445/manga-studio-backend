package com.mangaflow.studio.dto.series.request;

import com.mangaflow.studio.model.series.InvitationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * ── RespondInvitationRequest ──
 * DTO nhận dữ liệu từ client khi ASSISTANT phản hồi lời mời tham gia series.
 * <p>
 * 📌 Dùng trong:
 *    PATCH /api/assistants/invitations/{id}
 *    AssistantInvitationController.respondToInvitation()
 * <p>
 * 📌 Validation:
 *    status → @NotNull (bắt buộc), chỉ chấp nhận ACCEPTED hoặc REJECTED
 *    (validate ở Service layer — vì @NotNull không kiểm tra được giá trị enum)
 */
@Data
public class RespondInvitationRequest {

    /**
     * status: Trạng thái phản hồi của assistant.
     * <p>
     * Chỉ chấp nhận 2 giá trị:
     *   - ACCEPTED: đồng ý tham gia series → có thể nhận task
     *   - REJECTED: từ chối tham gia → mangaka có thể mời lại sau
     * <p>
     * @NotNull: bắt buộc phải gửi.
     * Giá trị PENDING không hợp lệ ở đây (chỉ dùng khi tạo lời mời).
     * <p>
     * Service sẽ kiểm tra:
     *   - status phải là ACCEPTED hoặc REJECTED → nếu PENDING → 400
     *   - Lời mời phải đang ở trạng thái PENDING → nếu không → 400
     *   - Lời mời phải thuộc về assistant đang login → nếu không → 403
     */
    @NotNull(message = "Status is required")
    private InvitationStatus status;
}
