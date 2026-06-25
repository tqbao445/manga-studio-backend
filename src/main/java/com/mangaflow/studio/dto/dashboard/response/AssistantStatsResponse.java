package com.mangaflow.studio.dto.dashboard.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ── AssistantStatsResponse ──
 * DTO chứa thống kê dashboard dành cho ASSISTANT.
 * <p>
 * Đây là response trả về khi user có role ASSISTANT gọi
 * GET /api/v1/dashboard/stats
 * <p>
 * ════════════════════════════════════════════════════════════
 *  Các field giải thích:
 * ════════════════════════════════════════════════════════════
 *  myTasks          → tổng số task được giao
 *  inProgress       → số task đang làm (IN_PROGRESS)
 *  todo             → số task chưa bắt đầu (TODO)
 *  done             → số task đã hoàn thành (DONE)
 *  assignedSeries   → danh sách series đang tham gia (đã ACCEPTED lời mời)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantStatsResponse {

    /**
     * Tổng số task được giao cho assistant này.
     * Tổng = todo + inProgress + done + submitted + revise.
     */
    private long myTasks;

    /**
     * Số task đang làm (IN_PROGRESS).
     * Assistant đã nhận việc và đang thực hiện.
     */
    private long inProgress;

    /**
     * Số task chưa bắt đầu (TODO).
     * Mới được giao, chưa ai nhận làm.
     */
    private long todo;

    /**
     * Số task đã hoàn thành (DONE) trong thời gian gần đây.
     * Cho assistant biết mình đã làm được bao nhiêu.
     */
    private long done;

    /**
     * Danh sách series mà assistant đã ACCEPTED lời mời.
     * Chỉ chứa thông tin cơ bản: seriesId, seriesTitle, coverImageUrl.
     */
    private List<SeriesBasicDTO> assignedSeries;
}
