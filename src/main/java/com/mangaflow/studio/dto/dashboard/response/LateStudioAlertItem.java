package com.mangaflow.studio.dto.dashboard.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * ── LateStudioAlertItem ──
 * Cảnh báo studio đang chậm deadline, hiển thị trong
 * dashboard stats của TANTOU_EDITOR.
 * <p>
 * Điều kiện kích hoạt: progressPercent < 50 AND daysLeft <= 3.
 * Tantou có thể dùng nút "Quick Nudge" để nhắc nhở.
 */
@Data
@Builder
@AllArgsConstructor
@Schema(description = "Cảnh báo studio chậm deadline (Late Studios Alert)")
public class LateStudioAlertItem {

    @Schema(description = "ID của tác giả", example = "11")
    private Long authorId;

    @Schema(description = "Tên tác giả", example = "Kaito")
    private String authorName;

    @Schema(description = "ID của series", example = "5")
    private Long seriesId;

    @Schema(description = "Tên series", example = "Eternal Blade")
    private String seriesTitle;

    @Schema(description = "ID của chapter", example = "42")
    private Long chapterId;

    @Schema(description = "Tiêu đề chapter", example = "Storm Break")
    private String chapterTitle;

    @Schema(description = "Số chapter", example = "12")
    private Integer chapterNumber;

    @Schema(description = "Tiến độ %", example = "35")
    private Integer progressPercent;

    @Schema(description = "Hạn chót", example = "2026-07-01")
    private LocalDate deadline;

    @Schema(description = "Số ngày còn lại", example = "2")
    private Long daysLeft;
}
