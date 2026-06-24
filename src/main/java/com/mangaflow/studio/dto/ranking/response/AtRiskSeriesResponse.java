package com.mangaflow.studio.dto.ranking.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ── AtRiskSeriesResponse ──
 * DTO trả về danh sách series đang AT_RISK (cảnh báo nguy cơ bị huỷ).
 * <p>
 * Dùng cho endpoint: GET /api/ranking/at-risk
 * <p>
 * Ngoài thông tin cơ bản của series, còn kèm:
 * - currentRank + totalSeries → EB biết series đang ở đâu
 * - scheduleType → biết series đang phát hành WEEKLY hay MONTHLY
 * - consecutiveDownPeriods → số kỳ liên tiếp bị tụt hạng
 * - recentRanks → lịch sử 5-10 kỳ gần nhất để EB đánh giá
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtRiskSeriesResponse {

    /**
     * ID của series.
     */
    private Long seriesId;

    /**
     * Tên series.
     */
    private String seriesTitle;

    /**
     * Màu nền fallback (khi không có ảnh bìa).
     */
    private String coverColor;

    /**
     * URL ảnh bìa (Cloudinary / S3).
     */
    private String coverImageUrl;

    /**
     * Thứ hạng hiện tại của series (kỳ gần nhất).
     * Lấy từ Series.currentRank, được cập nhật sau mỗi lần import ranking.
     */
    private int currentRank;

    /**
     * Tổng số series đang ONGOING / AT_RISK trong kỳ hiện tại.
     * Dùng để tính %: currentRank / totalSeries → EB biết series ở bottom bao nhiêu %.
     */
    private int totalSeries;

    /**
     * Loại lịch phát hành: "WEEKLY" hoặc "MONTHLY".
     * Lấy từ PublicationSchedule của series đó.
     * EB dùng để quyết định có nên giảm tần suất (WEEKLY → MONTHLY) hay không.
     */
    private String scheduleType;

    /**
     * Số kỳ liên tiếp bị trend = DOWN.
     * Nếu >= 2 thì series đã bị auto flag AT_RISK.
     * Nếu = 0, 1 thì có thể do EB tự set thủ công.
     */
    private int consecutiveDownPeriods;

    /**
     * Danh sách lịch sử xếp hạng 5-10 kỳ gần nhất.
     * Mỗi phần tử gồm: periodLabel, rank, trend (UP/DOWN/SAME/NEW).
     * EB dùng để nhìn xu hướng dài hạn, không chỉ dựa vào 2 kỳ.
     */
    private List<RankHistoryEntry> recentRanks;

    // ════════════════════════════════════════════════════════════
    //  Inner class: RankHistoryEntry
    // ════════════════════════════════════════════════════════════

    /**
     * ── RankHistoryEntry ──
     * 1 dòng trong lịch sử xếp hạng của 1 series.
     * <p>
     * VD: periodLabel="2026-W25", rank=9, trend="DOWN"
     * → "Tuần W25 xếp hạng 9, đang tụt so với kỳ trước"
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RankHistoryEntry {

        /**
         * Nhãn kỳ: "2026-W25" cho WEEKLY, "2026-06" cho MONTHLY.
         */
        private String periodLabel;

        /**
         * Thứ hạng trong kỳ này (1 = cao nhất).
         * Nếu chưa có rank (null) → mặc định 0.
         */
        private int rank;

        /**
         * Xu hướng so với kỳ trước:
         * - "UP"   ↑: rank tăng (cải thiện)
         * - "DOWN" ↓: rank giảm (tụt hạng)
         * - "SAME" →: rank không đổi
         * - "NEW"    : kỳ đầu tiên, không có kỳ trước để so sánh
         */
        private String trend;
    }
}
