package com.mangaflow.studio.dto.dashboard.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ── MangakaStatsResponse ──
 * DTO chứa thống kê dashboard dành cho MANGAKA.
 * <p>
 * Đây là response trả về khi user có role MANGAKA gọi
 * GET /api/v1/dashboard/stats
 * <p>
 * ════════════════════════════════════════════════════════════
 *  Các field giải thích:
 * ════════════════════════════════════════════════════════════
 *  activeSeries         → số series đang hoạt động (ONGOING + AT_RISK + HIATUS)
 *  ongoingChapters      → số chapter đang IN_PROGRESS
 *  pendingTasks         → số task chưa xử lý (TODO + IN_PROGRESS)
 *  submissionsToReview  → số task đã nộp chờ review (SUBMITTED)
 *  upcomingDeadlines    → danh sách deadline chapter sắp tới
 *  currentRank          → thứ hạng hiện tại của mangaka (từ ranking)
 *  rankTrend            → xu hướng: "UP" / "DOWN" / "SAME"
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MangakaStatsResponse {

    /**
     * Số series đang hoạt động (ONGOING + AT_RISK + HIATUS).
     * Cho mangaka biết tổng quan mình đang quản lý bao nhiêu series.
     */
    private long activeSeries;

    /**
     * Số chapter đang trong trạng thái IN_PROGRESS.
     * Cho mangaka biết có bao nhiêu chapter đang được thực hiện.
     */
    private long ongoingChapters;

    /**
     * Số task chưa xử lý (TODO + IN_PROGRESS).
     * Cho mangaka biết có bao nhiêu việc cần làm.
     */
    private long pendingTasks;

    /**
     * Số task đã được ASSISTANT nộp và đang chờ review (SUBMITTED).
     * Cho mangaka biết có bao nhiêu bài cần duyệt.
     */
    private long submissionsToReview;

    /**
     * Danh sách deadline chapter sắp tới (7-14 ngày tới).
     * Giúp mangaka không bỏ lỡ hạn chót.
     */
    private List<UpcomingDeadlineResponse> upcomingDeadlines;

    /**
     * Thứ hạng hiện tại (từ WEEKLY hoặc MONTHLY ranking).
     * Null nếu chưa có dữ liệu ranking.
     */
    private Integer currentRank;

    /**
     * Xu hướng thứ hạng so với kỳ trước:
     * - "UP"   → thứ hạng tăng (đang cải thiện)
     * - "DOWN" → thứ hạng giảm (đang tụt)
     * - "SAME" → không đổi
     * - "NEW"  → kỳ đầu tiên
     */
    private String rankTrend;
}
