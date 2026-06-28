package com.mangaflow.studio.dto.feedback.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ── ReaderFeedbackResponse ──
 * DTO trả về cho GET /api/v1/series/{seriesId}/reader-feedback.
 * <p>
 * ══════════════════════════════════════════════════════════════════
 *  Mục đích:
 * ══════════════════════════════════════════════════════════════════
 *  Cung cấp dữ liệu cho box "Reader Feedback" trong Survival Radar
 *  của Mangaka Dashboard. Box này hiển thị:
 *    - Các nhận xét tích cực nổi bật (highlights)
 *    - Các vấn đề được độc giả phản ánh (topIssues)
 *    - Tóm tắt chung (summary — có thể do Editorial Board viết)
 * <p>
 * ══════════════════════════════════════════════════════════════════
 *  Cách backend build response:
 * ══════════════════════════════════════════════════════════════════
 *  1. Query ReaderFeedback WHERE seriesId = :id
 *  2. Phân loại: type = "POSITIVE" → highlights, type = "ISSUE" → topIssues
 *  3. Nếu có SeriesReaderSummary riêng → đọc summary
 *  4. updatedAt = max(createdAt) của tất cả feedback
 */
@Data
@Builder
@AllArgsConstructor
@Schema(description = "Phản hồi nổi bật từ độc giả cho 1 series")
public class ReaderFeedbackResponse {

    @Schema(description = "ID của series", example = "5")
    private Long seriesId;

    @Schema(description = "Danh sách nhận xét tích cực nổi bật",
            example = "[\"Pacing tốt, cliffhanger mạnh\"]")
    private List<String> highlights;

    @Schema(description = "Danh sách vấn đề độc giả phản ánh",
            example = "[\"Background thiếu chi tiết\"]")
    private List<String> topIssues;

    @Schema(description = "Tóm tắt chung về phản hồi",
            example = "Độc giả phản hồi tích cực về arc mới")
    private String summary;

    @Schema(description = "Thời điểm cập nhật gần nhất",
            example = "2026-06-28T09:30:00")
    private LocalDateTime updatedAt;
}
