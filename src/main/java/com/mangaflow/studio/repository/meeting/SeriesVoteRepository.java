package com.mangaflow.studio.repository.meeting;

import com.mangaflow.studio.model.meeting.SeriesVote;
import com.mangaflow.studio.model.meeting.VoteType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ── SeriesVoteRepository ──
 * Repository cho entity SeriesVote.
 *
 * Tại sao cần các method này?
 * - findByMeetingId: Lấy tất cả phiếu của 1 cuộc họp.
 * - findByMeetingIdAndVoterId: Kiểm tra user đã vote chưa (cho upsert).
 * - countByMeetingIdAndVote: Đếm số phiếu YES/NO để biết kết quả.
 * - deleteByMeetingId: Xoá phiếu khi hủy cuộc họp.
 */
@Repository
public interface SeriesVoteRepository extends JpaRepository<SeriesVote, Long> {

    List<SeriesVote> findByMeetingId(Long meetingId);

    Optional<SeriesVote> findByMeetingIdAndVoterId(Long meetingId, Long voterId);

    long countByMeetingIdAndVote(Long meetingId, VoteType vote);

    void deleteByMeetingId(Long meetingId);
}
