package com.mangaflow.studio.dto.meeting;

import com.mangaflow.studio.model.meeting.MeetingStatus;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class MeetingResponse {

    @Schema(description = "ID cuộc họp", example = "1")
    private Long id;

    @Schema(description = "ID series", example = "1")
    private Long seriesId;

    @Schema(description = "Tên series", example = "One Piece")
    private String seriesTitle;

    @Schema(description = "URL ảnh bìa series", example = "https://example.com/cover.jpg")
    private String seriesCoverImageUrl;

    @Schema(description = "Màu nền fallback của series", example = "#1a1a2e")
    private String seriesCoverColor;

    @Schema(description = "Tiêu đề cuộc họp", example = "Họp phê duyệt series: One Piece")
    private String title;

    @Schema(description = "Nội dung cuộc họp")
    private String description;

    @Schema(description = "Link phòng họp", example = "https://meet.google.com/abc-defg-hij")
    private String meetingLink;

    @Schema(description = "ID người tạo", example = "1")
    private Long createdById;

    @Schema(description = "Tên người tạo", example = "Admin")
    private String createdByName;

    @Schema(description = "Trạng thái cuộc họp")
    private MeetingStatus status;

    @Schema(description = "Thời gian bắt đầu", example = "2026-06-15T10:00:00")
    private LocalDateTime startedAt;

    @Schema(description = "Thời gian kết thúc")
    private LocalDateTime endedAt;

    @Schema(description = "Quyết định cuối", example = "APPROVED")
    private String decision;

    @Schema(description = "Thời điểm tạo")
    private LocalDateTime createdAt;

    @Schema(description = "Thời điểm cập nhật")
    private LocalDateTime updatedAt;

    @ArraySchema(schema = @Schema(implementation = ParticipantInfo.class))
    private List<ParticipantInfo> participants;

    @Schema(description = "Tổng hợp kết quả vote")
    private VoteSummary voteSummary;

    @Data
    @Builder
    @Schema(description = "Thông tin người tham dự")
    public static class ParticipantInfo {
        @Schema(description = "ID user", example = "2")
        private Long userId;

        @Schema(description = "Username", example = "editor1")
        private String username;

        @Schema(description = "Tên hiển thị", example = "Nguyễn Văn A")
        private String displayName;
    }

    @Data
    @Builder
    @Schema(description = "Tổng hợp kết quả vote")
    public static class VoteSummary {
        @Schema(description = "Tổng số phiếu đã vote", example = "4")
        private long totalVotes;

        @Schema(description = "Tổng số EDITORIAL_BOARD được mời (không tính Tantou)", example = "3")
        private long totalBoardMembers;

        @Schema(description = "Số phiếu YES", example = "3")
        private long yesCount;

        @Schema(description = "Số phiếu NO", example = "1")
        private long noCount;

        @ArraySchema(schema = @Schema(implementation = VoteDetail.class))
        private List<VoteDetail> details;

        @Data
        @Builder
        @Schema(description = "Chi tiết từng phiếu")
        public static class VoteDetail {
            @Schema(description = "ID người vote", example = "2")
            private Long voterId;

            @Schema(description = "Tên người vote", example = "Nguyễn Văn A")
            private String voterName;

            @Schema(description = "Lựa chọn", example = "YES")
            private String vote;

            @Schema(description = "Nhận xét")
            private String comment;

            @ArraySchema(schema = @Schema(implementation = ScoreDetail.class))
            private List<ScoreDetail> scores;

            @Data
            @Builder
            @Schema(description = "Điểm chi tiết")
            public static class ScoreDetail {
                @Schema(description = "Tên tiêu chí", example = "Nội dung kịch bản")
                private String criterionName;

                @Schema(description = "Điểm", example = "8")
                private int score;
            }
        }
    }
}
