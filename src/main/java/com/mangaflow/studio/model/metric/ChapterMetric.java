package com.mangaflow.studio.model.metric;

import com.mangaflow.studio.model.chapter.Chapter;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 📊 ChapterMetric - Bảng chứa dữ liệu metrics (chỉ số) theo từng chapter mỗi tháng.
 *
 * Đây là dữ liệu CHI TIẾT, khác với SeriesMetric là tổng hợp.
 * Mỗi chapter có 1 bản ghi duy nhất cho 1 tháng (unique: chapter_id + month).
 *
 * Cách dùng:
 * - Chief Board tải file Excel (chapter-level) → điền votes/avgScore từng chapter
 * - Hệ thống import → lưu ChapterMetric → aggregate lên SeriesMetric
 * - Sau đó SeriesMetric được dùng để tính compositeScore và ranking
 *
 * 🔑 Mỗi chapter chỉ có DUY NHẤT 1 bản ghi cho 1 tháng (unique: chapter_id + month).
 */
@Entity
@Table(name = "chapter_metrics",
       uniqueConstraints = @UniqueConstraint(columnNames = {"chapter_id", "month"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChapterMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Chapter mà bản ghi metric này thuộc về.
     * ManyToOne: Nhiều bản ghi metric (nhiều tháng) có thể thuộc về 1 chapter.
     * LAZY: Chỉ load dữ liệu chapter khi thực sự cần (tối ưu performance).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    /**
     * Tháng của dữ liệu, định dạng "YYYY-MM".
     * VD: "2026-06"
     * Kết hợp với chapter_id tạo thành unique constraint.
     */
    @Column(nullable = false, length = 7)
    private String month;

    /**
     * Số phiếu bầu (votes) mà chapter này nhận được trong tháng.
     */
    @Column(nullable = false)
    private Long votes;

    /**
     * Điểm trung bình (Average Score) của chapter này, từ 0 đến 10.
     */
    @Column(nullable = false)
    private Double avgScore;

    // ===== Timestamps tự động =====
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
