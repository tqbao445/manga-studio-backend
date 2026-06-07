package com.mangaflow.studio.repository.meeting;

import com.mangaflow.studio.model.meeting.SeriesVoteScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ── SeriesVoteScoreRepository ──
 * Repository cho entity SeriesVoteScore.
 *
 * Tại sao cần các method này?
 * - findByVoteId: Lấy điểm chi tiết của 1 phiếu.
 * - findByVoteIdIn: Lấy điểm của nhiều phiếu cùng lúc.
 *   Dùng khi tổng hợp kết quả vote cho Chief Editor.
 */
@Repository
public interface SeriesVoteScoreRepository extends JpaRepository<SeriesVoteScore, Long> {

    List<SeriesVoteScore> findByVoteId(Long voteId);

    List<SeriesVoteScore> findByVoteIdIn(List<Long> voteIds);
}
