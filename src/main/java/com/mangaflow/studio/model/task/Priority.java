package com.mangaflow.studio.model.task;

/**
 * ── Priority ──
 * Enum định nghĩa mức độ ưu tiên của 1 Task.
 * <p>
 * ═══════════════════════════════════════════════════════════════
 *  Các mức ưu tiên:
 * ═══════════════════════════════════════════════════════════════
 *  LOW    → Làm sau, không gấp
 *  MEDIUM → Bình thường (mặc định nếu không chọn)
 *  HIGH   → Cần làm sớm
 *  URGENT → Gấp, làm ngay
 * <p>
 * 📌 Khi tạo task mới:
 *    Nếu không gửi priority → backend tự gán MEDIUM
 * <p>
 * 📌 Trong danh sách tasks, frontend sẽ highlight màu
 *    theo mức ưu tiên:
 *    - LOW:    xám
 *    - MEDIUM: xanh dương
 *    - HIGH:   cam
 *    - URGENT: đỏ
 */
public enum Priority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT
}
