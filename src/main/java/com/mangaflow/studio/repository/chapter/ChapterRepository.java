package com.mangaflow.studio.repository.chapter;

import com.mangaflow.studio.model.chapter.Chapter;
import com.mangaflow.studio.model.chapter.ChapterStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChapterRepository extends JpaRepository<Chapter, Long> {

    List<Chapter> findBySeriesIdOrderByChapterNumberAsc(Long seriesId);

    Optional<Chapter> findBySeriesIdAndChapterNumber(Long seriesId, Integer chapterNumber);

    //hàm kiểm tra trùng số chapter
    boolean existsBySeriesIdAndChapterNumber(Long seriesId, Integer chapterNumber);

    //đếm total chapters (cập nhật series.chapterCount)
    long countBySeriesId(Long seriesId);

    //hàm ownership check cho MANGAKA
    Optional<Chapter> findByIdAndSeries_MangakaId(Long id, Long mangakaId);

    //load chapter kèm series (JOIN FETCH) để check owner/tantou
    @Query("SELECT c FROM Chapter c JOIN FETCH c.series WHERE c.id = :id")
    Optional<Chapter> findByIdWithSeries(@Param("id") Long id);

    //kiểm tra tantou có phải editor của chapter không
    Optional<Chapter> findByIdAndSeries_TantouEditorId(Long id, Long tantouEditorId);

    //lấy chapter PUBLISHED trong khoảng thời gian (dùng cho export form ranking)
    @Query("SELECT c FROM Chapter c WHERE c.series.id = :seriesId AND c.status = :status AND c.publishDate BETWEEN :start AND :end ORDER BY c.chapterNumber ASC")
    List<Chapter> findBySeriesIdAndStatusAndPublishDateBetween(
            @Param("seriesId") Long seriesId,
            @Param("status") ChapterStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // ════════════════════════════════════════════════════════════
    // CÁC METHOD DÀNH CHO DASHBOARD
    // ════════════════════════════════════════════════════════════

    /**
     * Đếm số chapter của 1 series theo trạng thái cụ thể.
     * Dùng trong Dashboard để đếm chapter IN_PROGRESS, IN_REVIEW, ...
     *
     * Spring Data JPA tự động parse:
     * - countBySeriesId  : COUNT(*) WHERE series_id = ?
     * - AndStatus        : AND status = ?
     *
     * @param seriesId ID của series
     * @param status   trạng thái cần đếm (VD: IN_PROGRESS)
     * @return số lượng chapter
     */
    long countBySeriesIdAndStatus(Long seriesId, ChapterStatus status);

    /**
     * Đếm số chapter theo danh sách trạng thái.
     * Dùng trong BoardStatsResponse để đếm chaptersPending
     * (tổng SUBMITTED + IN_REVIEW).
     *
     * @param statuses danh sách trạng thái cần đếm
     * @return số lượng chapter
     */
    long countByStatusIn(List<ChapterStatus> statuses);

    /**
     * Tìm chapter deadline sắp tới của 1 series (7-14 ngày).
     * Dùng trong MangakaStatsResponse.upcomingDeadlines.
     *
     * @param seriesId ID của series
     * @param statuses danh sách trạng thái (IN_PROGRESS, PLANNED, SUBMITTED)
     * @param from     ngày bắt đầu (now)
     * @param to       ngày kết thúc (now + 14 days)
     * @return danh sách chapter có deadline trong khoảng
     */
    @Query("SELECT c FROM Chapter c WHERE c.series.id = :seriesId AND c.status IN :statuses AND c.deadline BETWEEN :from AND :to ORDER BY c.deadline ASC")
    List<Chapter> findBySeriesIdAndStatusInAndDeadlineBetween(
            @Param("seriesId") Long seriesId,
            @Param("statuses") List<ChapterStatus> statuses,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Tìm chapter của series mà tantou editor quản lý, theo trạng thái.
     * Dùng để lấy chaptersInReviewList cho Editor dashboard.
     *
     * @param tantouEditorId ID của tantou editor
     * @param status         trạng thái chapter (VD: IN_REVIEW)
     * @return danh sách chapter
     */
    @Query("SELECT c FROM Chapter c JOIN c.series s WHERE s.tantouEditor.id = :tantouEditorId AND c.status = :status ORDER BY c.updatedAt DESC")
    List<Chapter> findByTantouEditorIdAndStatus(
            @Param("tantouEditorId") Long tantouEditorId,
            @Param("status") ChapterStatus status);
}
