package com.mangaflow.studio.model.task;

/**
 * ── TaskSubmissionStatus ──
 * Enum định nghĩa trạng thái của 1 bài nộp (submission).
 * <p>
 * ═══════════════════════════════════════════════════════════════
 *  Các trạng thái:
 * ═══════════════════════════════════════════════════════════════
 *  SUBMITTED         → ASSISTANT vừa nộp, đang chờ MANGAKA duyệt
 *  APPROVED          → MANGAKA chấp nhận bài nộp (task → DONE)
 *  REVISION_REQUIRED → MANGAKA yêu cầu sửa (task → IN_PROGRESS)
 * <p>
 * 📌 Không có REJECTED ở cấp submission vì:
 *    - REJECTED là trạng thái của task (từ chối toàn bộ)
 *    - Ở submission chỉ có APPROVED hoặc REVISION_REQUIRED
 *      (yêu cầu sửa → ASSISTANT nộp version mới)
 * <p>
 * 📌 Mỗi submission chỉ được duyệt 1 lần (SUBMITTED → APPROVED
 *    hoặc SUBMITTED → REVISION_REQUIRED). Không thể thay đổi
 *    sau khi đã duyệt.
 */
public enum TaskSubmissionStatus {
    SUBMITTED,
    APPROVED,
    REVISION_REQUIRED
}
