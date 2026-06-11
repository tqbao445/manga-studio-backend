package com.mangaflow.studio.dto.meeting;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * ── CriterionResponse ──
 * DTO trả về danh sách tiêu chí chấm điểm cho form vote.
 *
 * Mục đích:
 * - Cho frontend biết có những tiêu chí nào (id, name, weight, sortOrder).
 * - Frontend dùng id này để gửi trong VoteRequest.scores[].criterionId.
 * - Không lộ entity VoteCriterion trực tiếp ra ngoài (tránh circular + security).
 */
@Data
@Builder
public class CriterionResponse {

    @Schema(description = "ID của tiêu chí", example = "1")
    private Long id;

    @Schema(description = "Tên tiêu chí", example = "Nội dung kịch bản")
    private String name;

    @Schema(description = "Giải thích tiêu chí")
    private String description;

    @Schema(description = "Trọng số (1-5)", example = "3")
    private Integer weight;

    @Schema(description = "Thứ tự hiển thị", example = "1")
    private Integer sortOrder;
}
