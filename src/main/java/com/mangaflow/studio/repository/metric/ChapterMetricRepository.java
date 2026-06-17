package com.mangaflow.studio.repository.metric;

import com.mangaflow.studio.model.metric.ChapterMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 🗄️ ChapterMetricRepository - Lớp giao tiếp với database cho bảng chapter_metrics.
 *
 * Đây là tầng Repository (DAO), có nhiệm vụ truy vấn dữ liệu từ database.
 * JpaRepository đã cung cấp sẵn các method CRUD cơ bản (save, findAll, findById, delete...).
 * Các method custom dưới đây được Spring Data JPA tự động sinh code dựa trên tên method.
 */
@Repository
public interface ChapterMetricRepository extends JpaRepository<ChapterMetric, Long> {

    /**
     * Tìm metric của 1 chapter trong 1 tháng cụ thể.
     * Dùng cho upsert: kiểm tra đã có → update, chưa có → insert.
     *
     * @param chapterId ID của chapter
     * @param month     Tháng định dạng "YYYY-MM"
     * @return Optional<ChapterMetric> - Có thể có hoặc không
     */
    Optional<ChapterMetric> findByChapterIdAndMonth(Long chapterId, String month);

    /**
     * Lấy tất cả metrics của 1 tháng (tất cả chapters).
     * Dùng để aggregate dữ liệu chapter → series.
     *
     * @param month Tháng cần lấy ("YYYY-MM")
     * @return Danh sách ChapterMetric của tháng đó
     */
    List<ChapterMetric> findByMonth(String month);

    /**
     * Lấy tất cả ChapterMetric của 1 series trong 1 tháng.
     * Dùng để gom chapter-level data → tính series-level aggregate.
     * seriesId lấy qua: chapter.series.id
     * Spring Data JPA tự động parse "ChapterSeriesId" thành chapter.series.id
     *
     * @param seriesId ID của series
     * @param month    Tháng cần lấy
     * @return Danh sách ChapterMetric của series đó trong tháng
     */
    List<ChapterMetric> findByChapterSeriesIdAndMonth(Long seriesId, String month);

    /**
     * Kiểm tra đã có metric cho chapter + tháng này chưa.
     *
     * @param chapterId ID của chapter
     * @param month     Tháng cần kiểm tra
     * @return true nếu đã tồn tại
     */
    boolean existsByChapterIdAndMonth(Long chapterId, String month);
}
