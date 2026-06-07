package com.mangaflow.studio.repository.meeting;

import com.mangaflow.studio.model.meeting.MeetingParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
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
 */
@Repository
public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, Long> {

    List<MeetingParticipant> findByMeetingId(Long meetingId);

    Optional<MeetingParticipant> findByMeetingIdAndUserId(Long meetingId, Long userId);

    boolean existsByMeetingIdAndUserId(Long meetingId, Long userId);
}
