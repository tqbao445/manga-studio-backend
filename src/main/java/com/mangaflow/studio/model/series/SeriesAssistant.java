package com.mangaflow.studio.model.series;

import com.mangaflow.studio.model.auth.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * ── SeriesAssistant ──
 * Entity trung gian quản lý quan hệ giữa Series và Assistant.
 * <p>
 * 📌 Bảng "series_assistant" lưu danh sách assistant được mời vào series,
 *    kèm trạng thái lời mời (PENDING / ACCEPTED / REJECTED).
 * <p>
 * 📌 Mỗi cặp (series_id, assistant_id) chỉ tồn tại DUY NHẤT 1 record
 *    (ràng buộc UNIQUE) → tránh mời trùng.
 * <p>
 * 📌 Nếu assistant bị REJECTED rồi được mời lại → status cập nhật lại
 *    thành PENDING (UPDATE, không INSERT mới).
 * <p>
 * ════════════════════════════════════════════════════════════
 *  Luồng đời sống (Lifecycle):
 * ════════════════════════════════════════════════════════════
 *  1. MANGAKA gửi lời mời                      → status = PENDING
 *  2. ASSISTANT đồng ý                          → status = ACCEPTED
 *  3. ASSISTANT từ chối                         → status = REJECTED
 *  4. MANGAKA xoá assistant khỏi series         → DELETE record
 *  5. MANGAKA mời lại assistant đã từ chối      → status = PENDING (UPDATE)
 */
@Entity
@Table(name = "series_assistant",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"series_id", "assistant_id"},
           name = "UQ_SeriesAssistant"
       ))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeriesAssistant {

    /**
     * id: Khoá chính, tự động tăng (IDENTITY).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * series: Series mà assistant được mời tham gia.
     * <p>
     * N:1 với Series — NOT NULL.
     * Khi series bị xoá → CASCADE DELETE (xoá luôn các record series_assistant).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", nullable = false)
    private Series series;

    /**
     * assistant: Người được mời (phải có role ASSISTANT).
     * <p>
     * N:1 với User — NOT NULL.
     * LAZY fetch — tránh load không cần thiết.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assistant_id", nullable = false)
    private User assistant;

    /**
     * invitedBy: Người gửi lời mời (MANGAKA của series).
     * <p>
     * N:1 với User — NOT NULL.
     * Lưu lại để biết ai đã mời (audit log).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by", nullable = false)
    private User invitedBy;

    /**
     * status: Trạng thái lời mời hiện tại.
     * <p>
     * @Enumerated(STRING) → lưu tên enum dạng chữ: "PENDING", "ACCEPTED", "REJECTED".
     * Mặc định: PENDING (vừa tạo lời mời).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InvitationStatus status = InvitationStatus.PENDING;

    /**
     * invitedAt: Thời điểm gửi lời mời.
     * Set tự động trong @PrePersist.
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime invitedAt;

    /**
     * respondedAt: Thời điểm assistant phản hồi (ACCEPTED / REJECTED).
     * NULLABLE — ban đầu chưa có phản hồi.
     * Set bởi Service khi assistant respond.
     */
    private LocalDateTime respondedAt;

    /**
     * ── @PrePersist ──
     * JPA lifecycle callback — tự động chạy TRƯỚC KHI insert entity.
     * Set invitedAt = thời điểm hiện tại.
     */
    @PrePersist
    protected void onCreate() {
        this.invitedAt = LocalDateTime.now();
    }
}
