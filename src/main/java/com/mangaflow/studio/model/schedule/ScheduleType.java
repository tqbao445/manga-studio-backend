package com.mangaflow.studio.model.schedule;

/**
 * ── ScheduleType ──
 * Enum định nghĩa loại lịch phát hành cho series.
 *
 * ═══════════════════════════════════════════════════
 * WEEKLY  (0): Phát hành theo tuần.
 *               - Dùng field dayOfWeek (1=Mon..7=Sun) để xác định thứ trong tuần.
 *               - Cron job quét mỗi tuần 1 lần vào đúng thứ đã chọn.
 *
 * MONTHLY (1): Phát hành theo tháng.
 *               - Dùng field dayOfMonth (1..31) để xác định ngày trong tháng.
 *               - Cron job quét mỗi tháng 1 lần vào đúng ngày đã chọn.
 *               - Edge case: dayOfMonth > số ngày thực tế → lấy ngày cuối tháng.
 * ═══════════════════════════════════════════════════
 *
 * 📌 Lưu ý:
 *    - Mỗi PublicationSchedule chỉ có 1 ScheduleType.
 *    - ScheduleType được lưu dạng String trong DB (EnumType.STRING)
 *      để dễ đọc và maintain.
 */
public enum ScheduleType {

    /**
     * WEEKLY: Phát hành theo tuần.
     * Hệ thống quét vào một thứ cố định trong tuần (dayOfWeek).
     * VD: dayOfWeek = 2 → mỗi Thứ Ba hệ thống kiểm tra và publish.
     */
    WEEKLY,

    /**
     * MONTHLY: Phát hành theo tháng.
     * Hệ thống quét vào một ngày cố định trong tháng (dayOfMonth).
     * VD: dayOfMonth = 15 → ngày 15 mỗi tháng hệ thống kiểm tra và publish.
     */
    MONTHLY
}
