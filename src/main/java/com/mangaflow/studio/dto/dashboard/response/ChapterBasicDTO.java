package com.mangaflow.studio.dto.dashboard.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ── ChapterBasicDTO ──
 * DTO chứa thông tin cơ bản của một chapter.
 * <p>
 * Dùng trong EditorStatsResponse.chaptersInReviewList
 * để hiển thị danh sách chapter cần review.
 * <p>
 * ════════════════════════════════════════════════════════════
 *  Ví dụ response:
 * ════════════════════════════════════════════════════════════
 *  {
 *    "id": 101,
 *    "seriesId": 1,
 *    "seriesTitle": "Blade of Dawn",
 *    "chapterNumber": 25,
 *    "title": "The Final Battle",
 *    "status": "IN_REVIEW"
 *  }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterBasicDTO {

    /**
     * ID của chapter.
     */
    private Long id;

    /**
     * ID của series chứa chapter này.
     */
    private Long seriesId;

    /**
     * Tên series (để hiển thị).
     */
    private String seriesTitle;

    /**
     * Số chapter (VD: 25).
     */
    private Integer chapterNumber;

    /**
     * Tiêu đề chapter.
     */
    private String title;

    /**
     * Trạng thái hiện tại: "IN_REVIEW", "SUBMITTED", ...
     */
    private String status;
}
