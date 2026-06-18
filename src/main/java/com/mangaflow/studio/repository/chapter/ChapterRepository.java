package com.mangaflow.studio.repository.chapter;

import com.mangaflow.studio.model.chapter.Chapter;
import com.mangaflow.studio.model.chapter.ChapterStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
