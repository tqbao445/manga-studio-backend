package com.mangaflow.studio.dto.dashboard.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ── BoardStatsResponse ──
 * DTO chứa thống kê dashboard dành cho EDITORIAL_BOARD và CHIEF_EDITOR.
 * <p>
 * Đây là response trả về khi user có role EDITORIAL_BOARD hoặc CHIEF_EDITOR
 * gọi GET /api/v1/dashboard/stats
 * <p>
 * ════════════════════════════════════════════════════════════
 *  Các field giải thích:
 * ════════════════════════════════════════════════════════════
 *  totalActiveSeries  → tổng series đang hoạt động (ONGOING + AT_RISK)
 *  proposalsPending   → số đề xuất đang chờ duyệt (PENDING_TANTOU + PENDING_BOARD_VOTE)
 *  chaptersPending    → số chapter đang chờ xử lý (SUBMITTED + IN_REVIEW)
 *  atRiskSeries       → danh sách series bị AT_RISK (dùng lại DTO có sẵn)
 *  upcomingMeetings   → danh sách cuộc họp sắp tới
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardStatsResponse {

    /**
     * Tổng số series đang hoạt động (ONGOING + AT_RISK).
     * Cho Editorial Board biết tổng quan hệ thống.
     */
    private long totalActiveSeries;

    /**
     * Số đề xuất series mới đang chờ duyệt.
     * PENDING_TANTOU: chờ tantou editor duyệt.
     * PENDING_BOARD_VOTE: chờ editorial board vote.
     */
    private long proposalsPending;

    /**
     * Số chapter đang chờ xử lý.
     * SUBMITTED: mangaka đã nộp chờ review.
     * IN_REVIEW: đang trong quá trình review.
     */
    private long chaptersPending;

    /**
     * Danh sách series đang bị AT_RISK (cảnh báo nguy cơ huỷ).
     * Dùng lại DTO AtRiskSeriesResponse đã có sẵn từ module ranking.
     */
    private List<?> atRiskSeries;

    /**
     * Danh sách cuộc họp sắp tới (PENDING hoặc IN_PROGRESS).
     * Dùng lại MeetingResponse DTO đã có sẵn từ module meeting.
     */
    private List<?> upcomingMeetings;
}
