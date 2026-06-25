package com.mangaflow.studio.dto.dashboard.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ── PublicationQueueItem ──
 * DTO đại diện cho một mục trong lịch xuất bản sắp tới.
 * <p>
 * Dùng trong EditorStatsResponse.publicationQueue
 * để hiển thị lịch phát hành chapter cho tantou editor.
 * <p>
 * ════════════════════════════════════════════════════════════
 *  Ví dụ response:
 * ════════════════════════════════════════════════════════════
 *  {
 *    "seriesId": 1,
 *    "seriesTitle": "Blade of Dawn",
 *    "chapterNumber": 25,
 *    "scheduledDate": "2026-07-01"
 *  }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicationQueueItem {

    /**
     * ID của series (để frontend link tới trang series).
     */
    private Long seriesId;

    /**
     * Tên series (hiển thị trên UI).
     */
    private String seriesTitle;

    /**
     * Số chapter sẽ xuất bản.
     * VD: 25 → "Chapter 25 sắp ra mắt"
     */
    private int chapterNumber;

    /**
     * Ngày dự kiến xuất bản (dạng yyyy-MM-dd).
     * Lấy từ PublicationSchedule.startDate hoặc tính từ lịch.
     */
    private String scheduledDate;
}
