package com.mangaflow.studio.dto.dashboard.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * ── MangakaStatsResponse ──
 * Response cho GET /api/v1/dashboard/stats.
 * <p>
 * Đây là DTO tổng hợp — tuỳ role của user đang đăng nhập
 * mà các field được populate khác nhau:
 * <pre>
 *   MANGAKA        → activeSeries, ongoingChapters, pendingTasks,
 *                     submissionsToReview, upcomingDeadlines, currentRank, rankTrend
 *   TANTOU_EDITOR  → assignedSeries, pendingComments, chaptersInReviewList, lateStudiosAlert
 *   ASSISTANT      → (không dùng — dùng /api/tasks riêng)
 *   EDITORIAL_BOARD/CHIEF_EDITOR → (không dùng)
 * </pre>
 * <p>
 * ══════════════════════════════════════════════════════════════════
 *  Cách backend xây dựng response:
 * ══════════════════════════════════════════════════════════════════
 *  1. Xác định role từ JWT
 *  2. Nếu MANGAKA:
 *     - Đếm series (status = ONGOING | AT_RISK) → activeSeries
 *     - Đếm chapter của các series đó đang làm → ongoingChapters
 *     - Đếm task đang pending → pendingTasks
 *     - Đếm task có submission SUBMITTED → submissionsToReview
 *     - Query chapter deadline sắp tới (limit 5) → upcomingDeadlines
 *     - Lấy rank gần nhất từ SeriesPeriodMetric → currentRank + rankTrend
 *  3. Nếu TANTOU_EDITOR:
 *     - Đếm series được gán → assignedSeries
 *     - Đếm comment chưa đọc → pendingComments
 *     - Query chapter IN_REVIEW → chaptersInReviewList
 *     - Query chapter progress < 50% và daysLeft <= 3 → lateStudiosAlert
 */
@Data
@Builder
@AllArgsConstructor
@Schema(description = "Dashboard stats — tổng quan số liệu dashboard")
public class MangakaStatsResponse {

    // ─── MANGAKA fields ───
    @Schema(description = "Số series đang active", example = "3")
    private Long activeSeries;

    @Schema(description = "Số chapter đang thực hiện", example = "4")
    private Long ongoingChapters;

    @Schema(description = "Số task đang chờ xử lý", example = "6")
    private Long pendingTasks;

    @Schema(description = "Số bài nộp chờ duyệt", example = "2")
    private Long submissionsToReview;

    @Schema(description = "Danh sách deadline sắp đến (tối đa 5)")
    private List<DeadlineItem> upcomingDeadlines;

    @Schema(description = "Rank hiện tại", example = "18")
    private Integer currentRank;

    @Schema(description = "Xu hướng rank: UP, DOWN, FLAT", example = "UP")
    private String rankTrend;

    // ─── TANTOU_EDITOR fields ───
    @Schema(description = "Số series được gán", example = "3")
    private Long assignedSeries;

    @Schema(description = "Số comment đang chờ xử lý", example = "8")
    private Long pendingComments;

    @Schema(description = "Danh sách chapter đang review (Manuscript Review Queue)")
    private List<ChapterInReviewItem> chaptersInReviewList;

    @Schema(description = "Danh sách studio chậm deadline (Late Studios Alert)")
    private List<LateStudioAlertItem> lateStudiosAlert;
}
