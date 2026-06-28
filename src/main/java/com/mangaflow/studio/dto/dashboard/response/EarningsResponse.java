package com.mangaflow.studio.dto.dashboard.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * ── EarningsResponse ──
 * DTO cho 1 mục trong biểu đồ thu nhập của ASSISTANT.
 * <p>
 * Trả về từ GET /api/v1/dashboard/earnings?groupBy=week|month.
 * FE dùng để render Earning Statement chart.
 * <p>
 * Mỗi item = 1 khoảng thời gian (tuần/tháng) với tổng thu nhập
 * và số lượng task đã hoàn thành trong kỳ đó.
 */
@Data
@Builder
@AllArgsConstructor
@Schema(description = "1 mục trong biểu đồ thu nhập (Earning Statement)")
public class EarningsResponse {

    @Schema(description = "Nhãn hiển thị", example = "W1")
    private String label;

    @Schema(description = "Mã kỳ", example = "2026-W25")
    private String period;

    @Schema(description = "Tổng thu nhập (VND)", example = "35000")
    private long amount;

    @Schema(description = "Số task hoàn thành", example = "12")
    private long taskCount;
}
