package com.mangaflow.studio.model.meeting;

import com.mangaflow.studio.model.auth.VoteCriterion;
import jakarta.persistence.*;
import lombok.*;

/**
 * ── SeriesVoteScore ──
 * Điểm chi tiết của từng phiếu bầu theo từng tiêu chí.
 *
 * Tại sao cần bảng này?
 * - Mỗi EDITORIAL_BOARD member chấm điểm 1-10 cho mỗi tiêu chí.
 * - VD: Với tiêu chí "Nội dung" → chấm 8/10, "Vẽ" → 7/10.
 * - Một phiếu (SeriesVote) có nhiều điểm (SeriesVoteScore).
 * - UNIQUE(vote_id, criterion_id): không chấm 2 lần 1 tiêu chí.
 */
@Entity
@Table(name = "series_vote_score",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"vote_id", "criterion_id"},
           name = "UQ_SeriesVoteScore"
       ))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeriesVoteScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Phiếu bầu mà điểm này thuộc về.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vote_id", nullable = false)
    private SeriesVote vote;

    /**
     * Tiêu chí chấm điểm.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criterion_id", nullable = false)
    private VoteCriterion criterion;

    /**
     * Điểm số từ 1 đến 10.
     * 1 = kém nhất, 10 = xuất sắc.
     */
    @Column(nullable = false)
    private Integer score;
}
