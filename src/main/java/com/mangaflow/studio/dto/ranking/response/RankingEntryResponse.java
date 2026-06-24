package com.mangaflow.studio.dto.ranking.response;

import lombok.*;

/**
 * 📤 RankingEntryResponse - DTO trả về 1 dòng trong bảng xếp hạng.
 * <p>
 * Dùng chung cho cả WEEKLY và MONTHLY ranking.
 * Không còn tier, consecutiveWarningMonths.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankingEntryResponse {
    private int rank;                      // Thứ hạng (1, 2, 3, ...)
    private Long seriesId;                 // ID của series
    private String seriesTitle;            // Tên series
    private String mangakaName;            // Tên tác giả
    private String tantouEditorName;       // Tên editor phụ trách
    private String status;                 // Trạng thái series
    private Long totalVotes;               // Tổng số phiếu bầu trong kỳ
    private Double avgScore;               // Điểm trung bình (0-10)
    private Double score;                  // Điểm ranking = votes*0.7 + avgScore*100
    private String periodLabel;            // "2026-W25" hoặc "2026-06"
    private String periodType;             // "WEEKLY" hoặc "MONTHLY"
    private Integer previousRank;          // Rank ở kỳ trước (null nếu không có dữ liệu)
    private String trend;                  // "UP" / "DOWN" / "SAME" / "NEW"
    private String coverImageUrl;
    private String coverColor;
    private String scheduleType;             // "WEEKLY" hoặc "MONTHLY"
}
