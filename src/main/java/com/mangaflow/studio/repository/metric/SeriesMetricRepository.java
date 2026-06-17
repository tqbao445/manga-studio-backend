package com.mangaflow.studio.repository.metric;

import com.mangaflow.studio.model.metric.SeriesMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 🗄️ SeriesMetricRepository - Lớp giao tiếp với database cho bảng series_metrics.
 * <p>
 * Đây là tầng Repository (DAO), có nhiệm vụ truy vấn dữ liệu từ database.
 * JpaRepository đã cung cấp sẵn các method CRUD cơ bản (save, findAll, findById, delete...).
 * Các method custom dưới đây được Spring Data JPA tự động sinh code dựa trên tên method.
 */
@Repository
public interface SeriesMetricRepository extends JpaRepository<SeriesMetric, Long> {

    /**
     * Tìm metric của 1 series trong 1 tháng cụ thể.
     * Dùng để kiểm tra: nếu đã có thì cập nhật (update), chưa có thì tạo mới (insert).
     *
     * @param seriesId ID của series
     * @param month    Tháng định dạng "YYYY-MM"
     * @return Optional<SeriesMetric> - Có thể có hoặc không
     */
    Optional<SeriesMetric> findBySeriesIdAndMonth(Long seriesId, String month);

    /**
     * Lấy tất cả metrics của 1 series, sắp xếp theo tháng MỚI NHẤT trước.
     * Dùng cho màn hình lịch sử metric của từng series.
     *
     * @param seriesId ID của series
     * @return Danh sách metrics đã sắp xếp
     */
    List<SeriesMetric> findBySeriesIdOrderByMonthDesc(Long seriesId);

    /**
     * Lấy tất cả metrics của 1 tháng cụ thể (tất cả series).
     * ⭐ Đây là method QUAN TRỌNG NHẤT - được RankingService sử dụng
     * để lấy dữ liệu tính toán bảng xếp hạng hàng tháng.
     *
     * @param month Tháng cần lấy ("YYYY-MM")
     * @return Danh sách metrics của tất cả series trong tháng đó
     */
    List<SeriesMetric> findByMonth(String month);

    /**
     * Kiểm tra xem đã có metric cho series + tháng nào đó chưa.
     * Dùng trong quá trình import để biết là insert hay update.
     *
     * @param seriesId ID của series
     * @param month    Tháng cần kiểm tra
     * @return true nếu đã tồn tại
     */
    boolean existsBySeriesIdAndMonth(Long seriesId, String month);
}
