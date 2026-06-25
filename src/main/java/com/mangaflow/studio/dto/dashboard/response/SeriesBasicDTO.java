package com.mangaflow.studio.dto.dashboard.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ── SeriesBasicDTO ──
 * DTO chứa thông tin cơ bản của một series.
 * <p>
 * Dùng trong AssistantStatsResponse.assignedSeries
 * và EditorStatsResponse.assignedSeriesList.
 * <p>
 * Đây là phiên bản "rút gọn" của SeriesResponse,
 * chỉ chứa các field cần thiết cho dashboard.
 * <p>
 * ════════════════════════════════════════════════════════════
 *  Ví dụ response:
 * ════════════════════════════════════════════════════════════
 *  {
 *    "id": 1,
 *    "title": "Blade of Dawn",
 *    "status": "ONGOING",
 *    "coverImageUrl": "https://...",
 *    "coverColor": "#1a1a2e"
 *  }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeriesBasicDTO {

    /**
     * ID của series.
     */
    private Long id;

    /**
     * Tên series.
     */
    private String title;

    /**
     * Trạng thái hiện tại: "ONGOING", "HIATUS", "AT_RISK", ...
     */
    private String status;

    /**
     * URL ảnh bìa (từ Cloudinary).
     */
    private String coverImageUrl;

    /**
     * Màu nền fallback (nếu không có ảnh bìa).
     */
    private String coverColor;
}
