package com.mangaflow.studio.model.meeting;

import com.mangaflow.studio.model.auth.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * ── SeriesVote ──
 * Phiếu bầu của từng thành viên EDITORIAL_BOARD cho một cuộc họp.
 *
 * Tại sao cần bảng này?
 * - Mỗi EDITORIAL_BOARD member vote YES/NO sau khi họp.
 * - Mỗi người chỉ vote 1 lần / 1 cuộc họp (UNIQUE constraint).
 * - Có comment để ghi lý do vote.
 */
@Entity
@Table(name = "series_vote",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"meeting_id", "voter_id"},
           name = "UQ_SeriesVote"
       ))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeriesVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Cuộc họp mà phiếu bầu này thuộc về.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private SeriesMeeting meeting;

    /**
     * Người bỏ phiếu — phải có role EDITORIAL_BOARD hoặc CHIEF_EDITOR.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voter_id", nullable = false)
    private User voter;

    /**
     * Lựa chọn: YES (đồng ý) hoặc NO (phản đối).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private VoteType vote;

    /**
     * Nhận xét kèm theo phiếu.
     * VD: "Cốt truyện ổn nhưng art cần cải thiện"
     */
    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String comment;

    /**
     * Thời điểm bỏ phiếu.
     */
    @Column(nullable = false)
    private LocalDateTime votedAt;

    @PrePersist
    protected void onCreate() {
        this.votedAt = LocalDateTime.now();
    }
}
