package com.mangaflow.studio.model.feedback;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ── ReaderFeedback Entity ──
 * Lưu trữ phản hồi nổi bật từ độc giả về 1 series.
 * <p>
 * Mỗi record là 1 feedback item (có thể là lời khen, góp ý, hoặc issue),
 * gắn với 1 series. Nhiều feedback gộp lại tạo thành "Reader Feedback box"
 * trong Survival Radar của Mangaka.
 * <p>
 * 📌 @Entity: JPA entity → Hibernate tạo bảng "reader_feedback"
 * <p>
 * ══════════════════════════════════════════════════════════════════
 *  Quy trình nghiệp vụ:
 * ══════════════════════════════════════════════════════════════════
 *  1. Editorial Board / hệ thống nhập phản hồi từ độc giả
 *  2. Mỗi phản hồi được phân loại: POSITIVE (khen), ISSUE (góp ý)
 *  3. Mangaka xem box Reader Feedback trong Survival Radar dashboard
 *  4. FE gọi GET /api/v1/series/{seriesId}/reader-feedback
 *     → Backend gom nhóm: highlights = POSITIVE, topIssues = ISSUE
 */
@Entity
@Table(name = "reader_feedback")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReaderFeedback {

    /**
     * id: Khoá chính, tự động tăng (IDENTITY).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * seriesId: ID của series mà feedback này thuộc về.
     * Dùng raw ID thay vì @ManyToOne để tránh LAZY loading phức tạp.
     */
    @Column(name = "series_id", nullable = false)
    private Long seriesId;

    /**
     * content: Nội dung phản hồi của độc giả.
     * VD: "Pacing tốt, cliffhanger mạnh", "Background thiếu chi tiết"
     * TEXT type — không giới hạn độ dài.
     */
    @Column(columnDefinition = "NVARCHAR(MAX)", nullable = false)
    private String content;

    /**
     * type: Phân loại phản hồi.
     * POSITIVE → lời khen / highlight
     * ISSUE    → vấn đề / góp ý
     * <p>
     * Dùng String thay vì Enum để linh hoạt mở rộng.
     */
    @Column(nullable = false, length = 20)
    private String type;

    /**
     * source: Nguồn của feedback (VD: "Reader", "Reviewer", "Editor").
     * NULLABLE — không bắt buộc.
     */
    @Column(length = 100)
    private String source;

    /**
     * createdAt: Thời điểm feedback được tạo.
     * Tự động set khi persist.
     */
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * ── @PrePersist ──
     * Tự động set createdAt trước khi insert.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
