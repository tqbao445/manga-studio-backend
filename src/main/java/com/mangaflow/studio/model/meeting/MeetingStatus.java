package com.mangaflow.studio.model.meeting;

/**
 * ── MeetingStatus ──
 * Trạng thái của một cuộc họp phê duyệt series.
 *
 * PENDING:      Cuộc họp vừa được tạo, chưa diễn ra
 * IN_PROGRESS:  Cuộc họp đang diễn ra
 * COMPLETED:    Đã kết thúc — đã có decision từ Chief Editor
 * CANCELLED:    Bị huỷ — không sử dụng được
 */
public enum MeetingStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
