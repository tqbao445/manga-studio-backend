package com.mangaflow.studio.dto.dashboard.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ── EditorStatsResponse ──
 * DTO chứa thống kê dashboard dành cho TANTOU_EDITOR.
 * <p>
 * Đây là response trả về khi user có role TANTOU_EDITOR gọi
 * GET /api/v1/dashboard/stats
 * <p>
 * ════════════════════════════════════════════════════════════
 *  Các field giải thích:
 * ════════════════════════════════════════════════════════════
 *  assignedSeries       → số series được phân công làm tantou
 *  chaptersInReview     → số chapter đang chờ review (IN_REVIEW)
 *  pendingComments      → số comment chưa được giải quyết (ACTIVE)
 *  assignedSeriesList   → danh sách chi tiết các series được phân công
 *  chaptersInReviewList → danh sách chapter cần review
 *  publicationQueue     → lịch xuất bản sắp tới của các series
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditorStatsResponse {

    /**
     * Số series mà tantou editor này được phân công quản lý.
     * Lấy từ Series.tantouEditor = currentUser.
     */
    private long assignedSeries;

    /**
     * Số chapter đang trong trạng thái IN_REVIEW
     * thuộc các series mà tantou này quản lý.
     */
    private long chaptersInReview;

    /**
     * Số comment chưa được RESOLVED
     * trên các page của series được phân công.
     */
    private long pendingComments;

    /**
     * Danh sách các series được phân công (chi tiết).
     * Frontend dùng để render danh sách nhanh.
     */
    private List<SeriesBasicDTO> assignedSeriesList;

    /**
     * Danh sách chapter đang cần review.
     * Frontend dùng để render danh sách việc cần làm.
     */
    private List<ChapterBasicDTO> chaptersInReviewList;

    /**
     * Lịch xuất bản sắp tới.
     * Lấy từ PublicationSchedule của các series được phân công.
     */
    private List<PublicationQueueItem> publicationQueue;
}
