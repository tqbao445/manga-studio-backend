package com.mangaflow.studio.dto.dashboard.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ── ActivityFeedResponse ──
 * DTO đại diện cho một activity (hoạt động) trong activity feed.
 * <p>
 * Dùng cho endpoint GET /api/v1/dashboard/activity-feed
 * để hiển thị các hoạt động gần đây trên dashboard.
 * <p>
 * ════════════════════════════════════════════════════════════
 *  Ví dụ response:
 * ════════════════════════════════════════════════════════════
 *  {
 *    "id": 1,
 *    "userName": "Nguyen Van A",
 *    "message": "Đã tạo series mới 'Blade of Dawn'",
 *    "type": "SERIES_CREATED",
 *    "createdAt": "2026-06-24T10:30:00"
 *  }
 * <p>
 * ════════════════════════════════════════════════════════════
 *  Các type hiện tại:
 * ════════════════════════════════════════════════════════════
 *  SERIES_CREATED, CHAPTER_SUBMITTED, CHAPTER_APPROVED,
 *  TASK_ASSIGNED, TASK_SUBMITTED, TASK_APPROVED,
 *  COMMENT_ADDED, INVITATION_SENT, INVITATION_ACCEPTED,
 *  MEETING_CREATED, MEETING_COMPLETED, RANKING_UPDATED
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityFeedResponse {

    /**
     * ID của activity (tương ứng với ID trong Notification table).
     */
    private Long id;

    /**
     * Tên hiển thị của người thực hiện hành động.
     * Lấy từ User.displayName thông qua userId của Notification.
     */
    private String userName;

    /**
     * Nội dung mô tả hành động.
     * VD: "Đã tạo series mới 'Blade of Dawn'"
     */
    private String message;

    /**
     * Loại hành động.
     * Frontend dùng type này để render icon và màu sắc khác nhau.
     */
    private String type;

    /**
     * Thời điểm xảy ra hành động.
     * Frontend sẽ hiển thị relative time: "2 hours ago", "yesterday", ...
     */
    private LocalDateTime createdAt;
}
