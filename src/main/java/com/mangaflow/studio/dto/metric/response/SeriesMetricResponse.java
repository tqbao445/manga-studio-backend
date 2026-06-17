package com.mangaflow.studio.dto.metric.response;

import lombok.*;
import java.time.LocalDateTime;

/**
 * 📤 SeriesMetricResponse - DTO trả về dữ liệu metrics của series cho client.
 * <p>
 * DTO (Data Transfer Object) là object dùng để truyền dữ liệu giữa các tầng,
 * đặc biệt là trả về cho client qua API.
 * Không dùng entity trực tiếp để tránh lộ dữ liệu nhạy cảm và vòng lặp vô hạn (JSON cycle).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeriesMetricResponse {
    private Long id;                    // ID của bản ghi metric
    private Long seriesId;              // ID của series
    private String seriesTitle;         // Tên series (để client khỏi phải gọi API khác)
    private String month;               // Tháng "YYYY-MM"
    private Long totalVotes;            // Tổng số phiếu bầu
    private Double avgScore;            // Điểm trung bình (0-10)
    private Double compositeScore;      // Điểm tổng hợp (dùng để xếp hạng)
    private LocalDateTime createdAt;    // Thời điểm import
}
