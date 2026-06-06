package com.mangaflow.studio.model.series;

import com.mangaflow.studio.model.auth.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * ── SeriesTantouInvitation ──
 * Entity quản lý lời mời Tantou tham gia kiểm duyệt series.
 * <p>
 * 📌 Bảng "series_tantou_invitation" lưu danh sách tantou được mangaka mời
 *    vào duyệt series, kèm trạng thái lời mời (PENDING / ACCEPTED / REJECTED).
 * <p>
 * 📌 Khác với SeriesAssistant (mời ASSISTANT vào làm việc), entity này
 *    dành riêng cho TANTOU_EDITOR — người duyệt series trước khi đưa lên
 *    Editorial Board vote.
 * <p>
 * 📌 Mỗi cặp (series_id, tantou_id) chỉ tồn tại DUY NHẤT 1 record
 *    (ràng buộc UNIQUE) → tránh mời trùng.
 * <p>
 * 📌 Nếu tantou bị REJECTED rồi được mời lại → status cập nhật lại
 *    thành PENDING (UPDATE, không INSERT mới).
 * <p>
 * ══════════════════════════════════════════════════════════════════
 *  Luồng đời sống (Lifecycle):
 * ══════════════════════════════════════════════════════════════════
 *  1. MANGAKA gửi lời mời                      → status = PENDING
 *  2. TANTOU_EDITOR đồng ý                      → status = ACCEPTED
 *     → Đồng thời gán series.tantouEditor = tantou này
 *  3. TANTOU_EDITOR từ chối                     → status = REJECTED
 *  4. MANGAKA xoá tantou khỏi series            → DELETE record
 *     → Đồng thời xoá series.tantouEditor nếu trùng
 *  5. MANGAKA mời lại tantou đã từ chối         → status = PENDING (UPDATE)
 */
@Entity
@Table(name = "series_tantou_invitation",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"series_id", "tantou_id"},
           name = "UQ_SeriesTantouInvitation"
       ))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeriesTantouInvitation {

    /**
     * id: Khoá chính, tự động tăng (IDENTITY).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * series: Series mà tantou được mời vào duyệt.
     * <p>
     * N:1 với Series — NOT NULL.
     * Khi series bị xoá → CASCADE DELETE (xoá luôn các record invitation).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", nullable = false)
    private Series series;

    /**
     * tantou: Người được mời (phải có role TANTOU_EDITOR).
     * <p>
     * N:1 với User — NOT NULL.
     * LAZY fetch — tránh load không cần thiết.
     * Khi tantou ACCEPTED → gán vào series.tantouEditor.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tantou_id", nullable = false)
    private User tantou;

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
     * respondedAt: Thời điểm tantou phản hồi (ACCEPTED / REJECTED).
     * NULLABLE — ban đầu chưa có phản hồi.
     * Set bởi Service khi tantou respond.
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
