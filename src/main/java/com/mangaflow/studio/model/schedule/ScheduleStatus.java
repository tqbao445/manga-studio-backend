package com.mangaflow.studio.model.schedule;

/**
 * ── ScheduleStatus ──
 * Enum định nghĩa trạng thái của một lịch phát hành (PublicationSchedule).
 *
 * ═══════════════════════════════════════════════════
 * ACTIVE    (0): Lịch đang hoạt động.
 *                - Cron job sẽ quét và xử lý series này.
 *                - nextChapterNumber tự động tăng dần khi publish thành công.
 *                - missCount tăng nếu chapter đến hạn mà chưa publish được.
 *
 * PAUSED    (1): Lịch tạm dừng.
 *                - Cron job bỏ qua, không quét series này.
 *                - Giữ nguyên nextChapterNumber và missCount.
 *                - Có thể RESUME để tiếp tục.
 *                - Tự động set khi:
 *                    + Series chuyển sang HIATUS/CANCELLED/COMPLETED
 *                    + missCount >= 3 (quá nhiều lần trễ)
 *
 * COMPLETED (2): Lịch kết thúc.
 *                - Không thể RESUME.
 *                - Chỉ set thủ công bởi EDITORIAL_BOARD/CHIEF_EDITOR
 *                  khi series đã hoàn thành hết chapters.
 * ═══════════════════════════════════════════════════
 */
public enum ScheduleStatus {

    /**
     * ACTIVE: Lịch đang hoạt động, cron job quét và xử lý.
     * Đây là trạng thái mặc định khi tạo schedule.
     */
    ACTIVE,

    /**
     * PAUSED: Lịch tạm dừng, cron job bỏ qua.
     * Có thể RESUME để tiếp tục.
     */
    PAUSED,

    /**
     * COMPLETED: Lịch đã kết thúc, không thể RESUME.
     */
    COMPLETED
}
