package com.mangaflow.studio.repository.meeting;

import com.mangaflow.studio.model.meeting.SeriesMeeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ── SeriesMeetingRepository ──
 * Repository cho entity SeriesMeeting.
 *
 * Tại sao cần các method này?
 * - findBySeriesIdOrderByCreatedAtDesc: Xem lịch sử các cuộc họp của 1 series.
 * - findByCreatedById: Chief Editor xem các meeting mình đã tạo.
 */
@Repository
public interface SeriesMeetingRepository extends JpaRepository<SeriesMeeting, Long> {

    List<SeriesMeeting> findBySeriesIdOrderByCreatedAtDesc(Long seriesId);

    List<SeriesMeeting> findByCreatedByIdOrderByCreatedAtDesc(Long createdById);
}
