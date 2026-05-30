package com.mangaflow.studio.model.series;

/**
 * ── SeriesStatus ──
 * Enum định nghĩa tất cả trạng thái của một bộ truyện (series) trong hệ thống.
 *
 * ════════════════════════════════════════════════════════════════════════
 * Giá trị               │  Ý nghĩa                           │  Ai đổi được
 * ════════════════════════════════════════════════════════════════════════
 * DRAFT                 │  Bản nháp, đang soạn thảo          │  Mangaka
 * PENDING_APPROVAL      │  Đã gửi lên chờ duyệt              │  Mangaka (submit)
 * APPROVED              │  Đã được duyệt sơ bộ               │  Editorial Board
 * ONGOING               │  Đang phát hành định kỳ            │  Editorial Board
 * HIATUS                │  Tạm ngưng (có thể trở lại)        │  Editorial Board
 * CANCELLED             │  Huỷ bỏ vĩnh viễn                  │  Editorial Board
 * COMPLETED             │  Đã hoàn thành (kết thúc)          │  Editorial Board
 * REJECTED              │  Bị từ chối (quay về DRAFT)        │  Editorial Board
 * AT_RISK               │  Nguy cơ bị huỷ (ranking thấp)     │  Hệ thống / Editorial Board
 * ════════════════════════════════════════════════════════════════════════
 *
 * 📌 Luồng chính: DRAFT → PENDING_APPROVAL → APPROVED → ONGOING → COMPLETED
 * 📌 Luồng phụ: ONGOING ↔ HIATUS, ONGOING → AT_RISK → CANCELLED
 * 📌 Lưu ý: Status được lưu dạng String trong DB (EnumType.STRING)
 *           để dễ đọc và refactor.
 */
public enum SeriesStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    ONGOING,
    HIATUS,
    CANCELLED,
    COMPLETED,
    REJECTED,
    AT_RISK
}
