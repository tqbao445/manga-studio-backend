package com.mangaflow.studio.model.task;

/**
 * ── TaskStatus ──
 * Enum định nghĩa các trạng thái của 1 Task (công việc giao cho ASSISTANT).
 * <p>
 * ═══════════════════════════════════════════════════════════════
 *  State machine (luồng trạng thái):
 * ═══════════════════════════════════════════════════════════════
 * <pre>
 *     TODO ──────────→ IN_PROGRESS ──────────→ DONE
 *       │                   ▲
 *       │                   │
 *       └── (xoá được)      │
 *                      REJECTED ──────────────→ IN_PROGRESS (làm lại)
 * </pre>
 * <p>
 * 📌 TODO:       Vừa tạo, chưa ai nhận. Chỉ MANAGAKA xoá được.
 * 📌 IN_PROGRESS: ASSISTANT đang làm. Không xoá được.
 * 📌 DONE:       MANAGAKA đã duyệt. Hoàn thành.
 * 📌 REJECTED:   MANAGAKA từ chối, yêu cầu sửa. ASSISTANT có thể làm lại.
 * <p>
 * Chi tiết chuyển trạng thái:
 * <pre>
 *  TODO       → IN_PROGRESS : ASSISTANT nhận việc
 *  IN_PROGRESS → REJECTED   : MANAGAKA từ chối (yêu cầu sửa)
 *  REJECTED   → IN_PROGRESS : ASSISTANT làm lại
 *  (IN_PROGRESS → DONE qua cơ chế submission + review riêng)
 * </pre>
 */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE,
    REJECTED
}
