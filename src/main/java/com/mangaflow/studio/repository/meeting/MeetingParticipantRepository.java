package com.mangaflow.studio.repository.meeting;

import com.mangaflow.studio.model.meeting.MeetingParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ── MeetingParticipantRepository ──
 * Repository cho entity MeetingParticipant.
 *
 * Tại sao cần các method này?
 * - findByMeetingId: Lấy danh sách participant của 1 cuộc họp.
 * - findByMeetingIdAndUserId: Kiểm tra 1 user có được mời không.
 * - existsByMeetingIdAndUserId: Kiểm tra nhanh trước khi thêm.
 * - findByUserId: Lấy tất cả participant records của 1 user (dùng để load meetings của user).
 * - countBoardMembersByMeetingId: Đếm số EDITORIAL_BOARD trong meeting (dùng cho auto-complete).
 */
@Repository
public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, Long> {

    List<MeetingParticipant> findByMeetingId(Long meetingId);

    Optional<MeetingParticipant> findByMeetingIdAndUserId(Long meetingId, Long userId);

    boolean existsByMeetingIdAndUserId(Long meetingId, Long userId);

    /**
     * Tìm tất cả participant records của 1 user.
     * Dùng trong getMeetingsForUser() để biết user được mời vào những meeting nào.
     *
     * @param userId ID của user cần tra cứu
     * @return Danh sách MeetingParticipant (mỗi record = 1 lời mời vào 1 meeting)
     */
    @Query("SELECT mp FROM MeetingParticipant mp JOIN FETCH mp.meeting WHERE mp.user.id = :userId")
    List<MeetingParticipant> findByUserId(@Param("userId") Long userId);

    /**
     * Đếm số lượng participant có role EDITORIAL_BOARD trong 1 meeting.
     * Dùng trong castVote() để kiểm tra xem tất cả EB đã vote chưa → auto-complete.
     *
     * Chỉ đếm EDITORIAL_BOARD, không đếm TANTOU_EDITER hay CHIEF_EDITOR.
     *
     * @param meetingId ID của cuộc họp
     * @return Số lượng EDITORIAL_BOARD được mời
     */
    @Query("SELECT COUNT(mp) FROM MeetingParticipant mp JOIN mp.user u WHERE mp.meeting.id = :meetingId AND u.role = 'EDITORIAL_BOARD'")
    long countBoardMembersByMeetingId(@Param("meetingId") Long meetingId);
}
