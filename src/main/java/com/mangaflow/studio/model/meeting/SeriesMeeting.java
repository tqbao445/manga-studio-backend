package com.mangaflow.studio.model.meeting;

import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.model.series.Series;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * ── SeriesMeeting ──
 * Entity này lưu thông tin một cuộc họp phê duyệt series.
 *
 * Tại sao cần bảng này?
 * - Chief Editor tạo cuộc họp để Editorial Board bỏ phiếu cho series.
 * - Cuộc họp diễn ra trên nền tảng bên ngoài (Zoom/Meet),
 *   ở đây chỉ lưu link + thông tin.
 * - decision là quyết định cuối của Chief Editor sau khi vote xong.
 */
@Entity
@Table(name = "series_meeting")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeriesMeeting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Series được đem ra họp phê duyệt.
     * Mỗi cuộc họp chỉ dành cho 1 series.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", nullable = false)
    private Series series;

    /**
     * Tiêu đề cuộc họp:
     * VD: "Họp phê duyệt series: One Piece - Chap 1"
     * Hiển thị trên danh sách meeting cho tất cả participant.
     */
    @Column(nullable = false, length = 200)
    private String title;

    /**
     * Nội dung / agenda của cuộc họp.
     * Miêu tả chi tiết về những gì sẽ được thảo luận.
     */
    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String description;

    /**
     * Link phòng họp (Google Meet / Zoom / ...).
     * Bắt buộc — participant dùng link này để vào họp.
     */
    @Column(nullable = false, length = 500)
    private String meetingLink;

    /**
     * Người tạo cuộc họp — phải là CHIEF_EDITOR.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    /**
     * Trạng thái cuộc họp:
     * PENDING: chờ diễn ra
     * IN_PROGRESS: đang họp
     * COMPLETED: đã xong
     * CANCELLED: huỷ
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MeetingStatus status = MeetingStatus.PENDING;

    /**
     * Thời gian bắt đầu và kết thúc cuộc họp.
     */
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    /**
     * Quyết định cuối của Chief Editor sau khi vote:
     * APPROVED: public series (chuyển series sang ONGOING)
     * REJECTED: từ chối (chuyển series về DRAFT)
     * null: chưa quyết định
     *
     * Lưu ý: Đây là quyết định FINE của Chief Editor,
     * KHÔNG phải auto từ vote — Chief xem xét vote + score rồi tự quyết.
     */
    private String decision;

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
