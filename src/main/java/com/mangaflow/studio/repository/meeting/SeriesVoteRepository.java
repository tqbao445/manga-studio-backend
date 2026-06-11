package com.mangaflow.studio.repository.meeting;

import com.mangaflow.studio.model.meeting.SeriesVote;
import com.mangaflow.studio.model.meeting.VoteType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
 * - countBoardVotersByMeetingId: Đếm số EDITORIAL_BOARD đã vote (dùng cho auto-complete).
 */
@Repository
public interface SeriesVoteRepository extends JpaRepository<SeriesVote, Long> {

    List<SeriesVote> findByMeetingId(Long meetingId);

    Optional<SeriesVote> findByMeetingIdAndVoterId(Long meetingId, Long voterId);

    long countByMeetingIdAndVote(Long meetingId, VoteType vote);

    void deleteByMeetingId(Long meetingId);

    /**
     * Đếm số lượng EDITORIAL_BOARD đã bỏ phiếu cho 1 cuộc họp.
     * Dùng trong castVote() để so sánh với tổng số EB được mời.
     * Nếu bằng nhau → tất cả EB đã vote → auto-complete meeting.
     *
     * Chỉ đếm voter có role EDITORIAL_BOARD, bỏ qua TANTOU_EDITOR hay CHIEF_EDITOR.
     *
     * Tại sao dùng COUNT(DISTINCT)?
     * - Mỗi người chỉ vote 1 lần / meeting (UNIQUE constraint),
     *   nhưng DISTINCT đảm bảo an toàn nếu có bug upsert.
     *
     * @param meetingId ID của cuộc họp
     * @return Số lượng EDITORIAL_BOARD đã vote
     */
    @Query("SELECT COUNT(DISTINCT sv.voter.id) FROM SeriesVote sv JOIN sv.voter u WHERE sv.meeting.id = :meetingId AND u.role = 'EDITORIAL_BOARD'")
    long countBoardVotersByMeetingId(@Param("meetingId") Long meetingId);
}
