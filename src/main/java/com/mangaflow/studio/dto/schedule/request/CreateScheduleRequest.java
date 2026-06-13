package com.mangaflow.studio.dto.schedule.request;

import com.mangaflow.studio.model.schedule.ScheduleType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * ── CreateScheduleRequest ──
 * Request DTO cho API tạo lịch phát hành (POST /api/series/{seriesId}/schedule).
 *
 * ═══════════════════════════════════════════════════
 *  Validation logic (xử lý ở Service layer):
 *    - Nếu scheduleType = WEEKLY  → dayOfWeek bắt buộc (1..7)
 *    - Nếu scheduleType = MONTHLY → dayOfMonth bắt buộc (1..31)
 * ═══════════════════════════════════════════════════
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateScheduleRequest {

    /**
     * scheduleType: Loại lịch phát hành.
     * - WEEKLY:  phát hành theo tuần, cần dayOfWeek
     * - MONTHLY: phát hành theo tháng, cần dayOfMonth
     * Bắt buộc.
     */
    @NotNull(message = "Schedule type is required")
    private ScheduleType scheduleType;

    /**
     * dayOfWeek: Thứ trong tuần (1=Monday .. 7=Sunday).
     * Chỉ dùng khi scheduleType = WEEKLY.
     * Bắt buộc nếu WEEKLY, không dùng nếu MONTHLY.
     */
    private Integer dayOfWeek;

    /**
     * dayOfMonth: Ngày trong tháng (1..31).
     * Chỉ dùng khi scheduleType = MONTHLY.
     * Bắt buộc nếu MONTHLY, không dùng nếu WEEKLY.
     */
    private Integer dayOfMonth;

    /**
     * startDate: Ngày bắt đầu lịch phát hành.
     * Cron job sẽ xử lý từ ngày này trở đi.
     * Bắt buộc.
     */
    @NotNull(message = "Start date is required")
    private LocalDate startDate;
}
