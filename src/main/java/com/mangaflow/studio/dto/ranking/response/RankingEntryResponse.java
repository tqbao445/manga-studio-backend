package com.mangaflow.studio.dto.ranking.response;

import lombok.*;

/**
 * 📤 RankingEntryResponse - DTO trả về thông tin 1 series trong bảng xếp hạng.
 * <p>
 * Mỗi object tương ứng với 1 dòng trong bảng xếp hạng.
 * Được RankingService.toRankingEntry() tạo ra từ entity Series.
 * <p>
 * Các trường: thứ hạng, tier, thông tin series, tác giả, trạng thái...
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankingEntryResponse {
    private int rank;                      // Thứ hạng hiện tại (1, 2, 3, ...)
    private String tier;                   // Tier: S / A / B / C / D
    private Long seriesId;                 // ID của series
    private String seriesTitle;            // Tên series
    private String genre;                  // Thể loại
    private String mangakaName;            // Tên tác giả (mangaka)
    private String status;                 // Trạng thái series (ONGOING, AT_RISK, CANCELLED...)
    private Long totalVotes;               // Tổng số phiếu bầu tháng gần nhất
    private Double avgScore;               // Điểm trung bình tháng gần nhất
    private Double compositeScore;         // Điểm tổng hợp
    private Integer consecutiveWarningMonths;  // Số tháng liên tiếp ở tier D
}
