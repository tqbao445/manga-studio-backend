package com.mangaflow.studio.model.series;

/**
 * ── SeriesStatus ──
 * Enum định nghĩa tất cả trạng thái của một bộ truyện (series) trong hệ thống.
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * Giá trị               │  Ý nghĩa                              │  Ai đổi được
 * ═══════════════════════════════════════════════════════════════════════════════
 * DRAFT                 │  Bản nháp, đang soạn thảo             │  Mangaka
 * PENDING_TANTOU       │  Đã submit, chờ Tantou duyệt          │  Mangaka (submit)
 * PENDING_BOARD_VOTE   │  Tantou đã duyệt, chờ EB vote         │  Tantou (approve)
 * ONGOING               │  Đang phát hành định kỳ               │  Editorial Board
 * HIATUS                │  Tạm ngưng (có thể trở lại)           │  Editorial Board
 * CANCELLED             │  Huỷ bỏ vĩnh viễn                     │  Editorial Board
 * COMPLETED             │  Đã hoàn thành (kết thúc)             │  Editorial Board
 * AT_RISK               │  Nguy cơ bị huỷ (ranking thấp)        │  Hệ thống / Editorial Board
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 📌 Luồng chính (MỚI - 3 giai đoạn):
 *     DRAFT → PENDING_TANTOU (Tantou duyệt)
 *           → PENDING_BOARD_VOTE (EB vote)
 *           → ONGOING (EB finalize)
 *
 * 📌 Luồng cũ (giữ lại cho backward compatibility):
 *     DRAFT → PENDING_APPROVAL → APPROVED → ONGOING
 *     Các status PENDING_APPROVAL, APPROVED, REJECTED vẫn tồn tại
 *     trong enum để không break dữ liệu cũ trong DB, nhưng KHÔNG được
 *     dùng trong flow mới (Series Approval Workflow).
 *
 * 📌 Luồng phụ: ONGOING ↔ HIATUS, ONGOING → AT_RISK → CANCELLED
 *
 * 📌 Lưu ý: Status được lưu dạng String trong DB (EnumType.STRING)
 *           để dễ đọc và refactor.
 */
public enum SeriesStatus {

    /**
     * DRAFT (0): Bản nháp — Mangaka đang soạn thảo.
     * - Có thể chỉnh sửa, xoá.
     * - Chưa thể submit nếu chưa có Tantou được assign.
     */
    DRAFT,

    /**
     * PENDING_TANTOU (1): Đã submit, chờ Tantou duyệt.
     * - Mangaka đã gửi series cho Tantou review.
     * - Tantou assigned sẽ vào xem pages + comment + approve/reject.
     * - Không thể chỉnh sửa hoặc xoá ở trạng thái này.
     */
    PENDING_TANTOU,

    /**
     * PENDING_BOARD_VOTE (2): Tantou đã duyệt, chờ Editorial Board vote.
     * - Tất cả EB member có thể vote YES/NO.
     * - EB có thể tạo Meeting (Google Meet) để thảo luận.
     * - Vote xong → EB finalize: Publish (→ ONGOING) hoặc Từ chối (→ DRAFT).
     */
    PENDING_BOARD_VOTE,

    /**
     * ONGOING (3): Đang phát hành định kỳ.
     * - Series đã được duyệt và publish.
     * - Có thể chuyển sang HIATUS, AT_RISK, CANCELLED, COMPLETED.
     */
    ONGOING,

    /**
     * HIATUS (4): Tạm ngưng — có thể trở lại ONGOING sau.
     */
    HIATUS,

    /**
     * CANCELLED (5): Huỷ bỏ vĩnh viễn — không thể khôi phục.
     */
    CANCELLED,

    /**
     * COMPLETED (6): Đã hoàn thành — kết thúc series.
     */
    COMPLETED,

    /**
     * AT_RISK (7): Nguy cơ bị huỷ do ranking/performance thấp.
     * Cảnh báo cho Mangaka cần cải thiện.
     */
    AT_RISK,

    // ══════════════════════════════════════════════════════════════
    // CÁC STATUS CŨ (BACKWARD COMPATIBILITY)
    // Giữ lại để không break dữ liệu đã lưu trong DB.
    // KHÔNG dùng trong code mới.
    // ══════════════════════════════════════════════════════════════

    /**
     * ⚠️ PENDING_APPROVAL (cũ): Đã gửi lên chờ duyệt (flow cũ).
     * Không dùng trong flow mới. Giữ lại cho dữ liệu cũ trong DB.
     */
    PENDING_APPROVAL,

    /**
     * ⚠️ APPROVED (cũ): Đã được duyệt sơ bộ (flow cũ).
     * Không dùng trong flow mới. Giữ lại cho dữ liệu cũ trong DB.
     */
    APPROVED,

    /**
     * ⚠️ REJECTED (cũ): Bị từ chối (flow cũ).
     * Không dùng trong flow mới. Giữ lại cho dữ liệu cũ trong DB.
     */
    REJECTED
}
