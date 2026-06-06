package com.mangaflow.studio.dto.series.request;

import lombok.Data;

/**
 * ── TantouRejectRequest ──
 * DTO nhận dữ liệu từ client khi Tantou từ chối series.
 * <p>
 * 📌 Dùng trong:
 *    POST /api/series/{seriesId}/tantou/reject
 *    SeriesController.tantouReject()
 * <p>
 * 📌 reason là optional — tantou có thể nhập lý do hoặc không.
 *    Nếu có, frontend sẽ hiển thị cho mangaka biết tại sao bị từ chối.
 */
@Data
public class TantouRejectRequest {

    /**
     * reason: Lý do từ chối (không bắt buộc).
     * <p>
     * Sau này có thể dùng để:
     *   - Hiển thị cho mangaka biết cần sửa gì
     *   - Lưu vào lịch sử series
     *   - Thống kê lý do từ chối phổ biến
     */
    private String reason;
}
