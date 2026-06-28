package com.mangaflow.studio.dto.dashboard.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ── DeadlineItem ──
 * Đại diện cho 1 deadline sắp đến trong dashboard stats.
 * <p>
 * Dùng trong MangakaStatsResponse.upcomingDeadlines[]
 * để hiển thị block "Urgent Deadlines" trên Mangaka Dashboard.
 * <p>
 * Mỗi item = 1 chapter sắp hết hạn, kèm thông tin series + chapter.
 */
@Data
@Builder
@AllArgsConstructor
@Schema(description = "Thông tin 1 deadline sắp đến")
public class DeadlineItem {

    @Schema(description = "ID của chapter", example = "42")
    private Long chapterId;

    @Schema(description = "ID của series", example = "5")
    private Long seriesId;

    @Schema(description = "Tên series", example = "Eternal Blade")
    private String seriesTitle;

    @Schema(description = "Số chapter", example = "12")
    private Integer chapterNumber;

    @Schema(description = "Tiêu đề chapter", example = "Storm Break")
    private String title;

    @Schema(description = "Hạn chót (ISO-8601)", example = "2026-07-01T00:00:00")
    private LocalDateTime deadline;

    @Schema(description = "Số ngày còn lại", example = "2")
    private long daysLeft;
}
