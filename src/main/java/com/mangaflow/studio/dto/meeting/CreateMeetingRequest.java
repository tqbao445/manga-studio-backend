package com.mangaflow.studio.dto.meeting;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateMeetingRequest {

    @Schema(description = "ID của series cần họp", example = "1")
    @NotNull(message = "Series ID là bắt buộc")
    private Long seriesId;

    @Schema(description = "Tiêu đề cuộc họp", example = "Họp phê duyệt series: One Piece")
    @NotBlank(message = "Tiêu đề cuộc họp là bắt buộc")
    private String title;

    @Schema(description = "Nội dung / agenda cuộc họp", example = "Đánh giá nội dung, nét vẽ và tính sáng tạo")
    private String description;

    @Schema(description = "Link phòng họp (Zoom / Google Meet)", example = "https://meet.google.com/abc-defg-hij")
    @NotBlank(message = "Link phòng họp là bắt buộc")
    private String meetingLink;

    @Schema(description = "Thời gian họp dự kiến (ISO datetime)", example = "2026-06-15T10:00:00")
    private LocalDateTime startedAt;

    @Schema(description = "Danh sách user ID được mời tham dự", example = "[2, 3, 4]")
    @NotEmpty(message = "Phải có ít nhất 1 participant")
    private List<Long> participantIds;
}
