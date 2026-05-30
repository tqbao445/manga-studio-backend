package com.mangaflow.studio.dto.page.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Request để đổi số thứ tự của 1 page")
public class PageReorderRequest {

    @NotNull(message = "Page number is required")
    @Schema(description = "Số thứ tự mới của page (bắt đầu từ 1)", example = "3")
    private Integer pageNumber;
}
