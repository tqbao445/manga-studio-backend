package com.mangaflow.studio.dto.chapter.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
@Schema(description = "Request tạo chapter mới")
public class ChapterRequest {

    @Schema(description = "Số chapter (phải unique trong series)", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Chapter number is required")
    private Integer chapterNumber;

    @Schema(description = "Tên chapter", example = "The Beginning")
    private String title;

    @Schema(description = "Hạn chót", example = "2026-06-15")
    private LocalDate deadline;
}
