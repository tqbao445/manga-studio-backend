package com.mangaflow.studio.model.metric;

import com.mangaflow.studio.model.series.Series;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * ── SeriesPeriodMetric ──
 * Entity duy nhất cho cả WEEKLY và MONTHLY ranking.
 *
 * Mỗi bản ghi = 1 series trong 1 kỳ (tuần hoặc tháng).
 * Dữ liệu được sinh ra khi EB import file Excel.
 *
 * Unique: (series_id, period_label, period_type)
 *   → 1 series chỉ có 1 bản ghi cho 1 tuần + 1 cho 1 tháng
 */
@Entity
@Table(name = "series_period_metrics",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"series_id", "period_label", "period_type"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeriesPeriodMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Series mà bản ghi này thuộc về.
     * N:1 — nhiều metric (nhiều kỳ) cho 1 series.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", nullable = false)
    private Series series;

    /**
     * Nhãn kỳ: "2026-W25" cho WEEKLY, "2026-06" cho MONTHLY.
     */
    @Column(nullable = false, length = 10)
    private String periodLabel;

    /**
     * Loại kỳ: "WEEKLY" hoặc "MONTHLY".
     */
    @Column(nullable = false, length = 7)
    private String periodType;

    /**
     * Tổng số phiếu bầu của tất cả chapter trong kỳ này.
     */
    @Column(nullable = false)
    private Long totalVotes;

    /**
     * Điểm trung bình (0-10) của các chapter.
     */
    @Column(nullable = false)
    private Double avgScore;

    /**
     * Điểm tính theo công thức: totalVotes * 0.7 + avgScore * 100
     * Dùng để sắp xếp thứ hạng.
     */
    @Column(nullable = false)
    private Double score;

    /**
     * Thứ hạng trong kỳ này (1 = cao nhất).
     * Được tính sau khi import, sort score DESC.
     */
    private Integer rank;

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
