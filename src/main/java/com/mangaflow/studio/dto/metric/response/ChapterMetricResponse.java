package com.mangaflow.studio.dto.metric.response;

import lombok.*;
import java.time.LocalDateTime;

/**
 * 📤 ChapterMetricResponse - DTO trả về dữ liệu metrics của chapter cho client.
 *
 * DTO (Data Transfer Object) là object dùng để truyền dữ liệu giữa các tầng,
 * đặc biệt là trả về cho client qua API.
 * Không dùng entity trực tiếp để tránh lộ dữ liệu nhạy cảm và vòng lặp vô hạn (JSON cycle).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterMetricResponse {
    private Long id;                    // ID của bản ghi ChapterMetric
    private Long chapterId;             // ID của chapter
    private Long seriesId;              // ID của series (để client filter)
    private String seriesTitle;         // Tên series (tiện lợi)
    private Integer chapterNumber;      // Số thứ tự chapter
    private String chapterTitle;        // Tên chapter
    private String month;               // Tháng "YYYY-MM"
    private Long votes;                 // Số phiếu bầu của chapter này
    private Double avgScore;            // Điểm trung bình chapter (0-10)
    private LocalDateTime createdAt;    // Thời điểm import
}
