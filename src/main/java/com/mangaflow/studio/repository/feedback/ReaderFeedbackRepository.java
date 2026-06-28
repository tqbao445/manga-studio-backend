package com.mangaflow.studio.repository.feedback;

import com.mangaflow.studio.model.feedback.ReaderFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ── ReaderFeedbackRepository ──
 * Repository cho entity ReaderFeedback — đọc phản hồi độc giả từ DB.
 * <p>
 * 📌 extends JpaRepository<ReaderFeedback, Long>:
 *    CRUD cơ bản: findAll, findById, save, delete...
 * <p>
 * Các method query:
 * - findBySeriesId(seriesId): Lấy tất cả feedback của 1 series
 *   để FE phân loại highlights (POSITIVE) và topIssues (ISSUE)
 */
@Repository
public interface ReaderFeedbackRepository
        extends JpaRepository<ReaderFeedback, Long> {

    /**
     * Lấy tất cả phản hồi của 1 series, sắp xếp mới nhất trước.
     *
     * @param seriesId ID của series
     * @return Danh sách feedback (mới nhất trước)
     */
    List<ReaderFeedback> findBySeriesIdOrderByCreatedAtDesc(Long seriesId);
}
