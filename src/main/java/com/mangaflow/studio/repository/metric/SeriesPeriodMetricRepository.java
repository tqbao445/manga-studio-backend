package com.mangaflow.studio.repository.metric;

import com.mangaflow.studio.model.metric.SeriesPeriodMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ── SeriesPeriodMetricRepository ──
 * Repository cho entity SeriesPeriodMetric.
 *
 * Các method:
 * - findByPeriodLabelAndPeriodTypeOrderByScoreDesc: dùng khi tính rank (sort rồi gán)
 * - findByPeriodLabelAndPeriodTypeOrderByRank: lấy ranking đã sort sẵn
 * - findBySeriesIdAndPeriodLabelAndPeriodType: dùng để upsert
 */
@Repository
public interface SeriesPeriodMetricRepository
        extends JpaRepository<SeriesPeriodMetric, Long> {

    /**
     * Lấy tất cả metric của 1 kỳ, sắp xếp theo score giảm dần.
     * Dùng khi tính rank — vị trí trong list = rank.
     */
    List<SeriesPeriodMetric> findByPeriodLabelAndPeriodTypeOrderByScoreDesc(
            String periodLabel, String periodType);

    /**
     * Lấy tất cả metric của 1 kỳ, sắp xếp theo rank tăng dần.
     * Dùng khi trả về UI (đã có rank sẵn).
     */
    List<SeriesPeriodMetric> findByPeriodLabelAndPeriodTypeOrderByRankAsc(
            String periodLabel, String periodType);

    /**
     * Kiểm tra series đã có metric cho kỳ này chưa.
     * Dùng để upsert (nếu có → update, chưa có → insert).
     */
    Optional<SeriesPeriodMetric> findBySeriesIdAndPeriodLabelAndPeriodType(
            Long seriesId, String periodLabel, String periodType);

    /**
     * Lấy lịch sử metric của 1 series (tất cả các kỳ).
     */
    List<SeriesPeriodMetric> findBySeriesIdOrderByPeriodLabelDesc(
            Long seriesId);
}
