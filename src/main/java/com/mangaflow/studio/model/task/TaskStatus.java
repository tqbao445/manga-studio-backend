package com.mangaflow.studio.model.task;

/**
 * ── TaskStatus ──
 * Enum định nghĩa các trạng thái của 1 Task (công việc giao cho ASSISTANT).
 * <p>
 * ═══════════════════════════════════════════════════════════════
 *  State machine (luồng trạng thái):
 * ═══════════════════════════════════════════════════════════════
 * <pre>
 *     TODO ──────────→ IN_PROGRESS ──────────→ SUBMITTED ────────→ DONE
 *       │                   ▲                      │
 *       │                   │                      │
 *       └── (xoá được)      │                      │ (yêu cầu sửa)
 *                      REVISE ◄─────────────────────┘
 *                         │
 *                         └──→ IN_PROGRESS (làm lại)
 * </pre>
 * <p>
 * 📌 TODO:       Vừa tạo, chưa ai nhận. Chỉ MANGAKA xoá được.
 * 📌 IN_PROGRESS: ASSISTANT đã nhận, đang làm.
 * 📌 SUBMITTED:  ASSISTANT đã nộp, đang chờ MANGAKA duyệt.
 * 📌 REVISE:     MANGAKA yêu cầu sửa lại.
 * 📌 DONE:       MANGAKA đã duyệt. Hoàn thành.
 * <p>
 * Chi tiết chuyển trạng thái:
 * <pre>
 *  TODO       → IN_PROGRESS : ASSISTANT nhận việc
 *  IN_PROGRESS → SUBMITTED  : ASSISTANT nộp bài
 *  SUBMITTED  → DONE        : MANGAKA duyệt (approve)
 *  SUBMITTED  → REVISE      : MANGAKA yêu cầu sửa
 *  REVISE     → IN_PROGRESS : ASSISTANT làm lại
 * </pre>
 */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    SUBMITTED,
    REVISE,
    DONE
}
