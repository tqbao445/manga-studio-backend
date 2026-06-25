package com.mangaflow.studio.dto.dashboard.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ── UpcomingDeadlineResponse ──
 * DTO đại diện cho một deadline chapter sắp tới.
 * <p>
 * Dùng trong MangakaStatsResponse.upcomingDeadlines
 * để hiển thị danh sách hạn chót cho mangaka.
 * <p>
 * ════════════════════════════════════════════════════════════
 *  Ví dụ response:
 * ════════════════════════════════════════════════════════════
 *  {
 *    "chapterId": 101,
 *    "title": "Ch.25",
 *    "deadline": "2026-07-05",
 *    "daysLeft": 10
 *  }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpcomingDeadlineResponse {

    /**
     * ID của chapter (để frontend có thể link tới chi tiết).
     */
    private Long chapterId;

    /**
     * Tiêu đề hiển thị: "Ch.25" hoặc "Ch.25 - The Final Battle".
     */
    private String title;

    /**
     * Ngày hạn chót (dạng yyyy-MM-dd).
     * deadline ở entity Chapter là LocalDate.
     */
    private String deadline;

    /**
     * Số ngày còn lại đến hạn.
     * daysLeft = deadline - today.
     * Nếu âm → đã quá hạn.
     */
    private long daysLeft;
}
