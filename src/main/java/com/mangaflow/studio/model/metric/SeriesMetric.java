package com.mangaflow.studio.model.metric;

import com.mangaflow.studio.model.series.Series;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 📊 SeriesMetric - Bảng chứa dữ liệu metrics (chỉ số) hàng tháng của từng series.
 * <p>
 * Đây là dữ liệu đầu vào quan trọng nhất cho hệ thống xếp hạng (Ranking).
 * Mỗi tháng, Editorial Board sẽ upload file Excel chứa dữ liệu bình chọn
 * của độc giả (tổng số phiếu bầu, điểm trung bình), và dữ liệu đó được
 * lưu vào bảng này.
 * <p>
 * 🔑 Mỗi series chỉ có DUY NHẤT 1 bản ghi cho 1 tháng (unique: series_id + month).
 * 📈 compositeScore được tính từ totalVotes và avgScore dùng để xếp hạng.
 */
@Entity
@Table(name = "series_metrics",
       uniqueConstraints = @UniqueConstraint(columnNames = {"series_id", "month"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeriesMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                          // ID tự tăng, khóa chính

    /**
     * Series mà bản ghi metric này thuộc về.
     * ManyToOne: Nhiều bản ghi metric (nhiều tháng) có thể thuộc về 1 series.
     * LAZY: Chỉ load dữ liệu series khi thực sự cần (tối ưu performance).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", nullable = false)
    private Series series;

    /**
     * Tháng của dữ liệu, định dạng "YYYY-MM".
     * VD: "2025-01", "2025-06", ...
     * Kết hợp với series_id tạo thành unique constraint (1 series chỉ có 1 bản ghi/tháng).
     */
    @Column(nullable = false, length = 7)
    private String month;

    /**
     * Tổng số phiếu bầu (votes) mà độc giả đã bình chọn cho series trong tháng này.
     * VD: 1500 nghĩa là có 1500 lượt bình chọn.
     */
    @Column(nullable = false)
    private Long totalVotes;

    /**
     * Điểm trung bình (Average Score) từ 0 đến 10.
     * VD: 8.5 nghĩa là độc giả đánh giá trung bình 8.5/10 cho series.
     * Đây là chất lượng cảm nhận từ độc giả.
     */
    @Column(nullable = false)
    private Double avgScore;

    /**
     * 🏆 Hạng (Tier) của series trong tháng này: S / A / B / C / D.
     * Được RankingService gán khi chạy calculateRankings().
     * Lưu lại để có lịch sử tier theo tháng (dùng cho cột Prev_Tier khi export).
     */
    private String tier;

    /**
     * 🎯 Điểm tổng hợp (Composite Score) - ĐÂY LÀ CHỈ SỐ QUAN TRỌNG NHẤT.
     * <p>
     * Công thức: compositeScore = totalVotes * 0.6 + (totalVotes * avgScore / 10.0) * 0.4
     * <p>
     * Giải thích:
     * - 60% trọng số cho số lượng phiếu bầu (totalVotes) → series càng nhiều người đọc càng tốt
     * - 40% trọng số cho chất lượng (avgScore) → điểm càng cao càng tốt
     * - compositeScore càng cao → xếp hạng càng cao
     * <p>
     * VD: totalVotes=1000, avgScore=8.0
     *   = 1000 * 0.6 + (1000 * 8.0 / 10.0) * 0.4
     *   = 600 + (800) * 0.4
     *   = 600 + 320
     *   = 920
     */
    private Double compositeScore;

    // ===== Timestamps tự động =====
    @Column(updatable = false)
    private LocalDateTime createdAt;          // Thời điểm tạo bản ghi (chỉ set 1 lần)

    private LocalDateTime updatedAt;          // Thời điểm cập nhật gần nhất

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
