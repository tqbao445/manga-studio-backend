package com.mangaflow.studio.dto.dashboard.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ── ChapterInReviewItem ──
 * Đại diện cho 1 chapter đang trong quá trình review
 * trong dashboard stats của TANTOU_EDITOR.
 * <p>
 * Hiển thị trong block "Manuscript Review Queue":
 * danh sách chapter đang chờ Tantou đọc và góp ý.
 */
@Data
@Builder
@AllArgsConstructor
@Schema(description = "Chapter đang trong quá trình review (cho Tantou)")
public class ChapterInReviewItem {

    @Schema(description = "ID của chapter", example = "42")
    private Long id;

    @Schema(description = "Số chapter", example = "12")
    private Integer chapterNumber;

    @Schema(description = "Tiêu đề chapter", example = "Storm Break")
    private String title;

    @Schema(description = "Tên series", example = "Eternal Blade")
    private String seriesTitle;

    @Schema(description = "Thời điểm nộp", example = "2026-06-27T13:00:00")
    private LocalDateTime submittedAt;

    @Schema(description = "Số trang", example = "16")
    private Integer pageCount;
}
