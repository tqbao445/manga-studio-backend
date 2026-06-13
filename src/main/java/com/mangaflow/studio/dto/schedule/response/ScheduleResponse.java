package com.mangaflow.studio.dto.schedule.response;

import com.mangaflow.studio.model.schedule.ScheduleStatus;
import com.mangaflow.studio.model.schedule.ScheduleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ── ScheduleResponse ──
 * Response DTO trả về thông tin chi tiết của một lịch phát hành.
 *
 * ═══════════════════════════════════════════════════
 *  Được dùng trong các API:
 *    - GET  /api/series/{seriesId}/schedule
 *    - GET  /api/schedules/{id}
 *    - POST /api/series/{seriesId}/schedule
 *    - PUT  /api/schedules/{id}
 *    - PATCH .../status
 *    - PATCH .../reset-miss
 * ═══════════════════════════════════════════════════
 */
@Data
@Builder
@AllArgsConstructor
public class ScheduleResponse {

    /**
     * id: ID của schedule.
     */
    private Long id;

    /**
     * seriesId: ID của series được lên lịch.
     * Dùng để frontend biết series nào.
     */
    private Long seriesId;

    /**
     * seriesTitle: Tên series (cache, tránh phải gọi API riêng).
     */
    private String seriesTitle;

    /**
     * scheduleType: Loại lịch — WEEKLY hoặc MONTHLY.
     */
    private ScheduleType scheduleType;

    /**
     * dayOfWeek: Thứ trong tuần (1=Mon..7=Sun).
     * Chỉ có giá trị khi scheduleType = WEEKLY.
     */
    private Integer dayOfWeek;

    /**
     * dayOfMonth: Ngày trong tháng (1..31).
     * Chỉ có giá trị khi scheduleType = MONTHLY.
     */
    private Integer dayOfMonth;

    /**
     * startDate: Ngày bắt đầu lịch phát hành.
     */
    private LocalDate startDate;

    /**
     * nextChapterNumber: Chapter tiếp theo cần publish.
     */
    private Integer nextChapterNumber;

    /**
     * missCount: Số lần trễ liên tiếp.
     */
    private Integer missCount;

    /**
     * status: Trạng thái lịch (ACTIVE / PAUSED / COMPLETED).
     */
    private ScheduleStatus status;

    /**
     * createdAt: Thời điểm tạo schedule.
     */
    private LocalDateTime createdAt;

    /**
     * updatedAt: Thời điểm cập nhật gần nhất.
     */
    private LocalDateTime updatedAt;
}
