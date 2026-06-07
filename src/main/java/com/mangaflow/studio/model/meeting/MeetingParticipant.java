package com.mangaflow.studio.model.meeting;

import com.mangaflow.studio.model.auth.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * ── MeetingParticipant ──
 * Danh sách người được mời vào cuộc họp.
 *
 * Tại sao cần bảng này?
 * - Chief Editor cần mời EDITORIAL_BOARD + TANTOU_EDITOR vào họp.
 * - Mỗi người chỉ được mời 1 lần cho 1 cuộc họp (UNIQUE constraint).
 * - Bảng này đơn giản chỉ ghi ai là participant của meeting nào.
 */
@Entity
@Table(name = "meeting_participant",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"meeting_id", "user_id"},
           name = "UQ_MeetingParticipant"
       ))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Cuộc họp được mời tham gia.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private SeriesMeeting meeting;

    /**
     * Người được mời (user bất kỳ trong hệ thống).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
