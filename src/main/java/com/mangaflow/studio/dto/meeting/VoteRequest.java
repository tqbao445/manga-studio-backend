package com.mangaflow.studio.dto.meeting;

import com.mangaflow.studio.model.meeting.VoteType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class VoteRequest {

    @Schema(description = "Lựa chọn: YES = ủng hộ, NO = phản đối", example = "YES")
    @NotNull(message = "Vote (YES/NO) là bắt buộc")
    private VoteType vote;

    @Schema(description = "Nhận xét kèm phiếu", example = "Cốt truyện tốt, art cần cải thiện")
    private String comment;

    @ArraySchema(schema = @Schema(implementation = CriterionScore.class))
    @NotEmpty(message = "Phải chấm điểm ít nhất 1 tiêu chí")
    @Valid
    private List<CriterionScore> scores;

    @Data
    @Schema(description = "Điểm cho 1 tiêu chí")
    public static class CriterionScore {

        @Schema(description = "ID của tiêu chí", example = "1")
        @NotNull(message = "criterionId là bắt buộc")
        private Long criterionId;

        @Schema(description = "Điểm từ 1 đến 10", example = "8")
        @Min(value = 1, message = "Điểm thấp nhất là 1")
        @Max(value = 10, message = "Điểm cao nhất là 10")
        private int score;
    }
}
