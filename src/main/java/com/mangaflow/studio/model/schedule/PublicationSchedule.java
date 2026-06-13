package com.mangaflow.studio.model.schedule;

import com.mangaflow.studio.model.series.Series;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ── PublicationSchedule ──
 * Entity lưu lịch phát hành định kỳ cho một series.
 *
 * ═══════════════════════════════════════════════════
 *  Mỗi series chỉ có 1 schedule ACTIVE tại 1 thời điểm.
 *  Schedule quyết định:
 *    - Tần suất phát hành: WEEKLY (theo tuần) hoặc MONTHLY (theo tháng)
 *    - Ngày cụ thể: thứ mấy trong tuần / ngày mấy trong tháng
 *    - Chapter hiện tại đang xử lý (nextChapterNumber)
 *    - Số lần trễ liên tiếp (missCount) để cảnh báo / đề xuất hủy
 * ═══════════════════════════════════════════════════
 *
 * 📌 Liên kết:
 *    - series (N:1) → Series: series được lên lịch
 *
 * 📌 Lifecycle:
 *    - createdAt / updatedAt tự động bởi @PrePersist / @PreUpdate
 */
@Entity
@Table(name = "publication_schedule")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicationSchedule {

    /**
     * id: Khoá chính, tự động tăng (IDENTITY).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * series: Series được lên lịch phát hành (N:1).
     * LAZY fetch — chỉ load khi cần.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", nullable = false)
    private Series series;

    /**
     * scheduleType: Loại lịch — WEEKLY hoặc MONTHLY.
     * Lưu dạng String trong DB.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false)
    private ScheduleType scheduleType;

    /**
     * dayOfWeek: Thứ trong tuần (1=Monday .. 7=Sunday).
     * Chỉ dùng khi scheduleType = WEEKLY.
     * NULL khi scheduleType = MONTHLY.
     */
    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    /**
     * dayOfMonth: Ngày trong tháng (1..31).
     * Chỉ dùng khi scheduleType = MONTHLY.
     * NULL khi scheduleType = WEEKLY.
     * Edge case: nếu dayOfMonth > số ngày thực tế → lấy ngày cuối tháng.
     */
    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    /**
     * startDate: Ngày bắt đầu lịch phát hành.
     * Cron job chỉ xử lý từ ngày này trở đi.
     */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /**
     * nextChapterNumber: Chapter tiếp theo cần publish.
     * Bắt đầu = 1 khi tạo schedule.
     * Tăng dần mỗi khi publish thành công.
     * Giữ nguyên khi chapter trễ (để tuần sau thử lại).
     */
    @Builder.Default
    @Column(name = "next_chapter_number", nullable = false)
    private Integer nextChapterNumber = 1;

    /**
     * missCount: Số lần trễ liên tiếp.
     * Reset về 0 mỗi khi publish thành công.
     * Tăng khi chapter đến hạn mà chưa publish được.
     * Khi missCount >= 3 → schedule tự động PAUSED + đề xuất hủy series.
     */
    @Builder.Default
    @Column(name = "miss_count", nullable = false)
    private Integer missCount = 0;

    /**
     * status: Trạng thái lịch (ACTIVE / PAUSED / COMPLETED).
     * Mặc định ACTIVE khi tạo.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ScheduleStatus status = ScheduleStatus.ACTIVE;

    /**
     * createdAt: Thời điểm tạo.
     * Không cho phép update sau khi insert.
     */
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * updatedAt: Thời điểm cập nhật gần nhất.
     * Tự động cập nhật mỗi khi entity thay đổi.
     */
    private LocalDateTime updatedAt;

    /**
     * ── @PrePersist ──
     * JPA lifecycle callback — tự động chạy TRƯỚC KHI insert entity.
     * Set createdAt + updatedAt lần đầu.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * ── @PreUpdate ──
     * JPA lifecycle callback — tự động chạy TRƯỚC KHI update entity.
     * Chỉ set updatedAt (createdAt giữ nguyên).
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
