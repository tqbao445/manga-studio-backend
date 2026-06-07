package com.mangaflow.studio.dto.meeting;

import com.mangaflow.studio.model.meeting.VoteType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class VoteResponse {

    @Schema(description = "ID của cuộc họp", example = "1")
    private Long meetingId;

    @Schema(description = "ID của series", example = "1")
    private Long seriesId;

    @Schema(description = "Phiếu của user hiện tại", example = "YES")
    private VoteType myVote;

    @Schema(description = "Nhận xét của user hiện tại", example = "Cốt truyện tốt")
    private String myComment;

    @ArraySchema(schema = @Schema(implementation = CriterionScoreResponse.class))
    private List<CriterionScoreResponse> myScores;

    @Schema(description = "Tổng phiếu YES", example = "3")
    private long voteCountYes;

    @Schema(description = "Tổng phiếu NO", example = "1")
    private long voteCountNo;

    @Schema(description = "Tổng số thành viên hội đồng được mời", example = "5")
    private long totalBoardMembers;

    @Schema(description = "Quyết định hiện tại (null nếu chưa có)", example = "APPROVED")
    private String currentDecision;

    @Data
    @Builder
    @Schema(description = "Điểm chi tiết theo tiêu chí")
    public static class CriterionScoreResponse {
        @Schema(description = "ID tiêu chí", example = "1")
        private Long criterionId;

        @Schema(description = "Tên tiêu chí", example = "Nội dung kịch bản")
        private String criterionName;

        @Schema(description = "Điểm", example = "8")
        private int score;
    }
}
