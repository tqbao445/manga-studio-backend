package com.mangaflow.studio.dto.chapter.response;

import com.mangaflow.studio.model.chapter.ChapterStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@Schema(description = "Thông tin chi tiết của 1 chapter")
public class ChapterResponse {

    @Schema(description = "ID của chapter", example = "1")
    private Long id;

    @Schema(description = "ID của series chứa chapter", example = "1")
    private Long seriesId;

    @Schema(description = "Tên series (cache)", example = "One Piece")
    private String seriesTitle;

    @Schema(description = "Số chapter", example = "1")
    private Integer chapterNumber;

    @Schema(description = "Tên chapter", example = "The Beginning")
    private String title;

    @Schema(description = "Tổng số trang", example = "20")
    private Integer pageCount;

    @Schema(description = "% hoàn thành (0-100)", example = "0")
    private Integer progressPercent;

    @Schema(description = "Hạn chót", example = "2026-06-15")
    private LocalDate deadline;

    @Schema(description = "Ngày xuất bản", example = "2026-07-01T00:00:00")
    private LocalDateTime publishDate;

    @Schema(description = "Trạng thái chapter")
    private ChapterStatus status;

    @Schema(description = "Thời điểm tạo", example = "2026-05-30T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Thời điểm cập nhật", example = "2026-05-30T10:00:00")
    private LocalDateTime updatedAt;
}
