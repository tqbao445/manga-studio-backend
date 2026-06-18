package com.mangaflow.studio.service.ranking;

import com.mangaflow.studio.common.exception.AppException;
import com.mangaflow.studio.dto.ranking.response.RankingEntryResponse;
import com.mangaflow.studio.model.auth.Role;
import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.model.metric.SeriesMetric;

import com.mangaflow.studio.model.series.Series;
import com.mangaflow.studio.model.series.SeriesStatus;
import com.mangaflow.studio.repository.auth.UserRepository;
import com.mangaflow.studio.repository.metric.SeriesMetricRepository;
import com.mangaflow.studio.repository.series.SeriesRepository;
import com.mangaflow.studio.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 🏆 RankingService - Service tính toán và quản lý bảng xếp hạng series.
 * <p>
 * Service này chịu trách nhiệm:
 * 1️⃣ Tính toán bảng xếp hạng dựa trên compositeScore từ SeriesMetric
 * 2️⃣ Phân loại series vào các tier S/A/B/C/D dựa trên percentile
 * 3️⃣ Xử lý series bị rơi vào tier D (cảnh báo, đề nghị Chief Editor xem xét hủy)
 * 4️⃣ Gửi thông báo cho mangaka khi series bị cảnh báo
 * 5️⃣ Gửi thông báo cho Chief Editor khi series cần xem xét hủy
 * <p>
 * 🚫 KHÔNG tự động hủy series. Khi series ở tier D 3+ tháng liên tiếp,
 * hệ thống sẽ thông báo cho Chief Editor để quyết định thủ công.
 * <p>
 * 📌 KHÔNG có model/table riêng cho ranking.
 * Dữ liệu ranking được lưu trực tiếp trên entity Series:
 * - currentRank: thứ hạng hiện tại
 * - currentTier: tier hiện tại (S/A/B/C/D)
 * - consecutiveWarningMonths: số tháng liên tiếp ở tier D
 */
@Service
@RequiredArgsConstructor
public class RankingService {

    private final SeriesMetricRepository metricRepository;       // Lấy dữ liệu metrics hàng tháng
    private final SeriesRepository seriesRepository;             // Cập nhật rank/tier/status cho series
    private final UserRepository userRepository;                 // Tìm Chief Editor để gửi thông báo
    private final NotificationService notificationService;       // Gửi thông báo

    // ========================================================================
    // ⚙️ CORE: TÍNH TOÁN BẢNG XẾP HẠNG CHO 1 THÁNG
    // ========================================================================

    /**
     * 🎯 TÍNH TOÁN BẢNG XẾP HẠNG CHO MỘT THÁNG CỤ THỂ.
     * <p>
     * Đây là phương thức QUAN TRỌNG NHẤT của cả module Metric & Ranking.
     * Được gọi tự động sau khi import Excel thành công (từ SeriesMetricService).
     * <p>
     * 🔄 QUY TRÌNH XỬ LÝ (từng bước chi tiết):
     * <p>
     * BƯỚC 1 - Lấy dữ liệu:
     *   - Lấy tất cả SeriesMetric của tháng cần tính từ DB
     *   - Nếu không có dữ liệu → vẫn tiếp tục xử lý (không return)
     * <p>
     * BƯỚC 1B - Auto fill 0-score cho series ONGOING/AT_RISK không có metric:
     *   - Tìm tất cả series ONGOING/AT_RISK
     *   - Series nào chưa có SeriesMetric trong tháng này → tạo metric với
     *     totalVotes=0, avgScore=0, compositeScore=0
     *   - Lưu vào DB để series này vẫn được xếp hạng, xuống D
     *   - Đảm bảo series "lười" không thể trốn khỏi ranking
     * <p>
     * BƯỚC 2 - Tính lại compositeScore:
     *   - Mỗi metric: compositeScore = totalVotes * 0.6 + (totalVotes * avgScore / 10.0) * 0.4
     *   - Lưu lại vào DB (phòng khi score chưa được tính trước đó)
     * <p>
     * BƯỚC 3 - Sắp xếp:
     *   - Tạo danh sách (Series + compositeScore)
     *   - Sắp xếp GIẢM DẦN theo compositeScore (cao nhất → thấp nhất)
     * <p>
     * BƯỚC 4 - Gán rank và tier cho từng series:
     *   - currentRank = vị trí (1-based): 1, 2, 3, ..., total
     *   - percentile = i / total (i là index 0-based)
     *   - VD: có 20 series, i=0 → 0% (S), i=1 → 5% (S), i=5 → 25% (A)...
     *   - Gán tier dựa trên percentile:
     *        < 10%    → S (Top 10% - Xuất sắc)
     *        10-30%   → A (Top 10-30% - Tốt)
     *        30-60%   → B (Top 30-60% - Trung bình khá)
     *        60-90%   → C (Top 60-90% - Trung bình)
     *        >= 90%   → D (Bottom 10% - Kém - có nguy cơ bị hủy)
     * <p>
     * BƯỚC 5 - Xử lý series tier D (cảnh báo, KHÔNG tự động hủy):
     *   - Nếu tier = D:
     *     a. Tăng consecutiveWarningMonths lên 1
     *     b. Chuyển status → AT_RISK (nếu chưa)
     *     c. Nếu >= 3 tháng liên tiếp:
     *         • 🟡 Gửi thông báo cho tất cả CHIEF_EDITOR yêu cầu xem xét hủy
     *         • 🟡 Gửi thông báo cho mangaka biết series đang chờ Chief Editor quyết định
     *         • ❌ KHÔNG tự động chuyển CANCELLED (Chief Editor làm thủ công qua API)
     *     d. Nếu < 3 tháng:
     *         • Gửi thông báo cảnh báo cho mangaka
     *   - Nếu tier ≠ D (S/A/B/C):
     *     a. Reset consecutiveWarningMonths = 0
     *     b. Nếu trước đó đang AT_RISK → chuyển lại về ONGOING
     * <p>
     * BƯỚC 6 - Lưu tất cả thay đổi vào DB
     * <p>
     * 📌 Chief Editor có thể dùng API PATCH /api/series/{id}/status để hủy thủ công.
     *
     * @param month Tháng cần tính xếp hạng (định dạng "YYYY-MM")
     */
    @Transactional
    public void calculateRankings(String month) {
        // ---- BƯỚC 1: Lấy tất cả metrics của tháng này ----
        List<SeriesMetric> metrics = new ArrayList<>(metricRepository.findByMonth(month));

        // ---- BƯỚC 1B: Auto fill 0-score cho series ONGOING/AT_RISK không có metric ----
        // Series không có chapter nào trong tháng → không có dữ liệu import → tự tạo 0-score
        // để series vẫn bị xếp hạng (thường xuống D) thay vì trốn khỏi ranking
        List<Series> activeSeries = seriesRepository.findByStatusIn(
                List.of(SeriesStatus.ONGOING, SeriesStatus.AT_RISK));
        Set<Long> seriesWithMetrics = metrics.stream()
                .map(m -> m.getSeries().getId())
                .collect(Collectors.toSet());

        for (Series series : activeSeries) {
            if (!seriesWithMetrics.contains(series.getId())) {
                SeriesMetric zeroMetric = SeriesMetric.builder()
                        .series(series)
                        .month(month)
                        .totalVotes(0L)
                        .avgScore(0.0)
                        .compositeScore(0.0)
                        .build();
                metricRepository.save(zeroMetric);
                metrics.add(zeroMetric);
            }
        }

        // Nếu không có bất kỳ series nào (cả imported + auto 0-score) → thoát
        if (metrics.isEmpty()) return;

        // ---- BƯỚC 2: Tính lại compositeScore cho mỗi metric ----
        for (SeriesMetric m : metrics) {
            // Công thức: 60% số lượng phiếu + 40% điểm chất lượng
            double score = m.getTotalVotes() * 0.6 + (m.getTotalVotes() * m.getAvgScore() / 10.0) * 0.4;
            m.setCompositeScore(score);
        }
        metricRepository.saveAll(metrics);

        // ---- BƯỚC 3: Sắp xếp series theo compositeScore giảm dần ----
        // Tạo list các cặp (Series, score) từ metrics
        List<SeriesScore> scored = metrics.stream()
                .map(m -> new SeriesScore(m.getSeries(), m.getCompositeScore()))
                .sorted((a, b) -> Double.compare(b.score, a.score))  // Cao nhất → thấp nhất
                .collect(Collectors.toList());

        int total = scored.size();  // Tổng số series có metrics trong tháng này

        // ---- Build lookup map: SeriesId → SeriesMetric (để lưu tier vào metric) ----
        Map<Long, SeriesMetric> metricMap = metrics.stream()
                .collect(Collectors.toMap(m -> m.getSeries().getId(), m -> m));

        // ---- BƯỚC 4 & 5: Gán rank, tier, xử lý cảnh báo cho từng series ----
        for (int i = 0; i < total; i++) {
            Series series = scored.get(i).series;  // Series ở vị trí thứ i
            double score = scored.get(i).score;    // compositeScore của series đó

            // Gán thứ hạng (1-based: vị trí 1 là cao nhất)
            series.setCurrentRank(i + 1);

            // Tính percentile = vị trí index / tổng số
            // VD: i=0, total=20 → 0/20 = 0.0 (0%) → S
            //     i=4, total=20 → 4/20 = 0.2 (20%) → A
            //     i=18, total=20 → 18/20 = 0.9 (90%) → D
            double percentile = (double) i / total;
            String tier;
            if (percentile < 0.1)           tier = "S";   // Top 10%
            else if (percentile < 0.3)      tier = "A";   // Top 10-30%
            else if (percentile < 0.6)      tier = "B";   // Top 30-60%
            else if (percentile < 0.9)      tier = "C";   // Top 60-90%
            else                            tier = "D";   // Bottom 10% (nguy hiểm)

            series.setCurrentTier(tier);

            // ✅ Lưu tier vào SeriesMetric để có lịch sử (dùng cho Prev_Tier khi export)
            SeriesMetric matchedMetric = metricMap.get(series.getId());
            if (matchedMetric != null) {
                matchedMetric.setTier(tier);
            }

            // ── publishFrequency đã được thay thế bằng PublicationSchedule (ScheduleType WEEKLY/MONTHLY) ──
            // Chief/EB chủ động quản lý schedule qua API. RankingService không can thiệp.

            // ---- XỬ LÝ SERIES THUỘC TIER D (CẢNH BÁO, KHÔNG TỰ ĐỘNG HỦY) ----
            if ("D".equals(tier)) {
                // Tăng số tháng liên tiếp ở tier D
                int current = series.getConsecutiveWarningMonths() != null
                        ? series.getConsecutiveWarningMonths() : 0;
                series.setConsecutiveWarningMonths(current + 1);

                // Luôn chuyển status → AT_RISK (nếu chưa) để đánh dấu nguy cơ
                series.setStatus(SeriesStatus.AT_RISK);

                if (series.getConsecutiveWarningMonths() >= 3) {
                    // 🟡 ĐÃ 3+ THÁNG LIÊN TIẾP Ở TIER D → BÁO CHIEF EDITOR XEM XÉT
                    // KHÔNG tự động hủy - Chief Editor quyết định thủ công qua API PATCH /api/series/{id}/status

                    // Gửi thông báo cho tất cả Chief Editor
                    List<User> chiefEditors = userRepository.findByRole(Role.CHIEF_EDITOR);
                    String ceTitle = "Series \"" + series.getTitle() + "\" needs review";
                    String ceMessage = "Series \"" + series.getTitle()
                            + "\" has been in tier D for " + series.getConsecutiveWarningMonths()
                            + " consecutive months. Please review and decide whether to cancel.";
                    for (User ce : chiefEditors) {
                        notificationService.createAndSend(
                                ce.getId(),
                                "CANCELLATION_REVIEW",
                                ceTitle,
                                ceMessage,
                                "SERIES",
                                series.getId()
                        );
                    }

                    // Gửi thông báo cho mangaka biết series đang chờ xem xét
                    notificationService.createAndSend(
                            series.getMangaka().getId(),
                            "PENDING_CANCELLATION_REVIEW",
                            "Series \"" + series.getTitle() + "\" is pending cancellation review",
                            "Your series has been in tier D for "
                            + series.getConsecutiveWarningMonths()
                            + " consecutive months. The Chief Editor will review and decide. "
                            + "Please improve the quality to avoid cancellation.",
                            "SERIES",
                            series.getId()
                    );
                } else {
                    // 🟡 MỚI 1-2 THÁNG → CẢNH BÁO CHO MANGAKA
                    notificationService.createAndSend(
                            series.getMangaka().getId(),
                            "WARNING_ISSUED",
                            "Series \"" + series.getTitle() + "\" is at risk",
                            "Your series is in tier D (bottom 10%) for month "
                            + (series.getConsecutiveWarningMonths()) + ". "
                            + "Improve to avoid cancellation after 3 consecutive months.",
                            "SERIES",
                            series.getId()
                    );
                }
            } else {
                // 🟢 SERIES Ở TIER S/A/B/C → KHÔNG SAO
                // Reset số tháng cảnh báo
                series.setConsecutiveWarningMonths(0);
                // Nếu trước đó đang AT_RISK thì phục hồi lại ONGOING
                if (series.getStatus() == SeriesStatus.AT_RISK) {
                    series.setStatus(SeriesStatus.ONGOING);
                }
            }

            // ---- Lưu thay đổi vào database ----
            seriesRepository.save(series);
        }

        // ---- Lưu tier vào tất cả metrics (để có lịch sử tier theo tháng) ----
        metricRepository.saveAll(metrics);
    }

    // ========================================================================
    // 📋 LẤY BẢNG XẾP HẠNG
    // ========================================================================

    /**
     * Lấy danh sách tất cả series đã có thứ hạng (currentRank != null).
     * Kết quả được sắp xếp theo rank tăng dần (1 → 2 → 3 → ...).
     *
     * @return Danh sách RankingEntryResponse đã sắp xếp
     */
    @Transactional(readOnly = true)
    public List<RankingEntryResponse> getRankings() {
        return getRankings(null);
    }

    @Transactional(readOnly = true)
    public List<RankingEntryResponse> getRankings(String month) {
        if (month != null) {
            List<SeriesMetric> metrics = metricRepository.findByMonth(month);
            metrics.sort(Comparator.comparingDouble(SeriesMetric::getCompositeScore).reversed());
            List<RankingEntryResponse> result = new ArrayList<>(metrics.size());
            for (int i = 0; i < metrics.size(); i++) {
                result.add(toRankingEntry(metrics.get(i), metrics.get(i).getSeries(), i + 1));
            }
            return result;
        }
        List<Series> allSeries = seriesRepository.findAll();
        return allSeries.stream()
                .filter(s -> s.getCurrentRank() != null)
                .sorted(Comparator.comparingInt(Series::getCurrentRank))
                .map(this::toRankingEntry)
                .collect(Collectors.toList());
    }

    // ========================================================================
    // 🏷️ LẤY RANKING THEO TIER
    // ========================================================================

    /**
     * Lấy bảng xếp hạng nhưng nhóm theo tier (S/A/B/C/D).
     * <p>
     * Sử dụng TreeMap để đảm bảo thứ tự hiển thị: S → A → B → C → D
     * (TreeMap tự động sắp xếp key theo thứ tự alphabet).
     *
     * @return Map<String, List<RankingEntryResponse>> - Key là tier, Value là danh sách series
     */
    @Transactional(readOnly = true)
    public Map<String, List<RankingEntryResponse>> getRankingsByTier() {
        List<RankingEntryResponse> all = getRankings();  // Lấy tất cả rankings
        return all.stream()
                .collect(Collectors.groupingBy(
                        RankingEntryResponse::getTier,        // Nhóm theo tier
                        TreeMap::new,                         // Giữ thứ tự S < A < B < C < D
                        Collectors.toList()                   // Gom vào List
                ));
    }

    // ========================================================================
    // ⚖️ CHIEF BOARD QUYẾT ĐỊNH GIỮ/HỦY SERIES TIER D
    // ========================================================================

    /**
     * Xử lý quyết định của Chief Editor khi series ở tier D >= 3 tháng.
     * <p>
     * Chief Editor có 2 lựa chọn:
     * 1. KEEP: Giữ lại series → reset consecutiveWarningMonths = 0, chuyển về ONGOING
     * 2. CANCEL: Hủy series → chuyển sang CANCELLED
     * <p>
     * Hành động này ghi lại ai đã quyết định và gửi thông báo cho Mangaka biết kết quả.
     *
     * @param seriesId  ID của series cần xử lý
     * @param decision  "KEEP" hoặc "CANCEL"
     * @param chiefUser User đang thực hiện (lấy từ JWT token)
     */
    @Transactional
    public void handleCancelDecision(Long seriesId, String decision, User chiefUser) {
        // Tìm series theo ID
        Series series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Series not found with id: " + seriesId));

        // Kiểm tra series có đang AT_RISK không (chỉ AT_RISK mới cần quyết định)
        if (series.getStatus() != SeriesStatus.AT_RISK) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Series is not AT_RISK. Current status: " + series.getStatus());
        }

        if ("KEEP".equalsIgnoreCase(decision)) {
            // 🟢 QUYẾT ĐỊNH GIỮ LẠI
            // Reset số tháng cảnh báo và đưa về trạng thái phát hành bình thường
            series.setConsecutiveWarningMonths(0);
            series.setStatus(SeriesStatus.ONGOING);
            series.setStatusNote("Retained by Chief Editor "
                    + chiefUser.getDisplayName() + " on " + java.time.LocalDate.now());

            // Gửi thông báo cho Mangaka biết series được giữ lại
            notificationService.createAndSend(
                    series.getMangaka().getId(),
                    "CANCELLATION_REVIEW_RESULT",
                    "Series \"" + series.getTitle() + "\" has been retained",
                    "The Chief Editor has decided to KEEP your series. "
                            + "Your warning has been reset. Please improve to avoid future warnings.",
                    "SERIES",
                    series.getId()
            );

        } else if ("CANCEL".equalsIgnoreCase(decision)) {
            // 🔴 QUYẾT ĐỊNH HỦY SERIES
            series.setStatus(SeriesStatus.CANCELLED);
            series.setStatusNote("Cancelled by Chief Editor "
                    + chiefUser.getDisplayName() + " on " + java.time.LocalDate.now());

            // Gửi thông báo cho Mangaka biết series đã bị hủy
            notificationService.createAndSend(
                    series.getMangaka().getId(),
                    "SERIES_CANCELLED",
                    "Series \"" + series.getTitle() + "\" has been cancelled",
                    "The Chief Editor has decided to CANCEL your series "
                            + "due to being in tier D for too long.",
                    "SERIES",
                    series.getId()
            );

        } else {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Invalid decision: " + decision + ". Must be 'KEEP' or 'CANCEL'");
        }

        // Lưu thay đổi vào database
        seriesRepository.save(series);
    }

    // ========================================================================
    // 🔄 MAP ENTITY → DTO
    // ========================================================================

    /**
     * Chuyển đổi entity Series thành DTO RankingEntryResponse để trả về client.
     * <p>
     * Map các trường cần thiết:
     * - rank, tier, seriesId, seriesTitle, genre, mangakaName, status...
     *
     * @param series Entity Series cần map
     * @return RankingEntryResponse DTO
     */
    private RankingEntryResponse toRankingEntry(Series series) {
        return RankingEntryResponse.builder()
                .rank(series.getCurrentRank() != null ? series.getCurrentRank() : 0)
                .tier(series.getCurrentTier() != null ? series.getCurrentTier() : "N/A")
                .seriesId(series.getId())
                .seriesTitle(series.getTitle())
                .genre(series.getGenres() != null && !series.getGenres().isEmpty()
                        ? series.getGenres().get(0).name() : null)
                .mangakaName(series.getMangaka() != null
                        ? (series.getMangaka().getDisplayName() != null
                            ? series.getMangaka().getDisplayName()
                            : series.getMangaka().getUsername())
                        : null)
                .status(series.getStatus() != null ? series.getStatus().name() : null)
                .consecutiveWarningMonths(series.getConsecutiveWarningMonths())
                .build();
    }

    private RankingEntryResponse toRankingEntry(SeriesMetric metric, Series series, int rank) {
        return RankingEntryResponse.builder()
                .rank(rank)
                .tier(metric.getTier() != null ? metric.getTier() :
                        (series.getCurrentTier() != null ? series.getCurrentTier() : "N/A"))
                .seriesId(series.getId())
                .seriesTitle(series.getTitle())
                .genre(series.getGenres() != null && !series.getGenres().isEmpty()
                        ? series.getGenres().get(0).name() : null)
                .mangakaName(series.getMangaka() != null
                        ? (series.getMangaka().getDisplayName() != null
                            ? series.getMangaka().getDisplayName()
                            : series.getMangaka().getUsername())
                        : null)
                .status(series.getStatus() != null ? series.getStatus().name() : null)
                .totalVotes(metric.getTotalVotes())
                .avgScore(metric.getAvgScore())
                .compositeScore(metric.getCompositeScore())
                .consecutiveWarningMonths(series.getConsecutiveWarningMonths())
                .build();
    }

    // ========================================================================
    // 🏠 CLASS PHỤ: Lưu cặp (Series, score) tạm thời để sắp xếp
    // ========================================================================

    /**
     * Class tạm dùng để lưu cặp (Series, compositeScore) trong quá trình sắp xếp.
     * Không phải entity, chỉ là POJO nội bộ.
     */
    @lombok.AllArgsConstructor
    private static class SeriesScore {
        Series series;       // Series
        double score;        // compositeScore của series đó
    }
}
