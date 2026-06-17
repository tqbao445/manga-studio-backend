package com.mangaflow.studio.model.series;

/**
 * 📅 PublishFrequency - Enum định nghĩa tần suất phát hành của series.
 * <p>
 * Được RankingService tự động gán sau mỗi kỳ tính xếp hạng:
 * - S, A → WEEKLY  (ra chap mỗi tuần, ~4 chap/tháng)
 * - B, C → BI_WEEKLY (ra chap 2 tuần/lần, ~2 chap/tháng)
 * - D → MONTHLY (ra chap mỗi tháng, ~1 chap/tháng)
 * <p>
 * Mục đích: Khuyến khích chất lượng — series tier cao được ra nhiều hơn,
 * series tier thấp bị giảm tần suất để tập trung cải thiện chất lượng.
 */
public enum PublishFrequency {
    WEEKLY,      // Hàng tuần (S, A)
    BI_WEEKLY,   // 2 tuần/lần (B, C)
    MONTHLY      // Hàng tháng (D)
}
