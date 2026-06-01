package com.mangaflow.studio.model.series;

/**
 * ── InvitationStatus ──
 * Trạng thái lời mời assistant tham gia series.
 * <p>
 * Dùng trong entity SeriesAssistant để quản lý luồng:
 *   MANGAKA mời → PENDING → ASSISTANT đồng ý → ACCEPTED
 *                         → ASSISTANT từ chối → REJECTED
 * <p>
 * ══════════════════════════════════════════════
 *  Quy tắc chuyển trạng thái (state machine):
 * ══════════════════════════════════════════════
 *  PENDING  → ACCEPTED  : Assistant đồng ý
 *  PENDING  → REJECTED  : Assistant từ chối
 *  REJECTED → PENDING   : Mangaka mời lại (re-invite)
 *  ACCEPTED → (xoá record) : Mangaka remove assistant
 * <p>
 *  Các trạng thái khác không hợp lệ:
 *    ACCEPTED → PENDING   ❌
 *    ACCEPTED → REJECTED  ❌
 *    REJECTED → ACCEPTED  ❌
 */
public enum InvitationStatus {

    /**
     * PENDING: Lời mời đang chờ assistant phản hồi.
     * Đây là trạng thái khởi tạo khi mangaka gửi lời mời.
     * Assistant có thể ACCEPT hoặc REJECT.
     */
    PENDING,

    /**
     * ACCEPTED: Assistant đã đồng ý tham gia series.
     * Chỉ khi ACCEPTED, assistant mới:
     *   - Xuất hiện trong danh sách series của họ (SeriesListPage)
     *   - Có thể được gán task trong series này (TaskService)
     */
    ACCEPTED,

    /**
     * REJECTED: Assistant đã từ chối lời mời.
     * Mangaka có thể mời lại (re-invite) → đưa về PENDING.
     */
    REJECTED
}
