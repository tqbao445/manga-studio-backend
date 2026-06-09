package com.mangaflow.studio.dto.page.request;

import com.mangaflow.studio.model.page.PageStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Request để cập nhật trạng thái page")
public class PageStatusRequest {

    @NotNull(message = "Status is required")
    @Schema(description = "Trạng thái mới của page (VD: COMPLETED)", example = "COMPLETED")
    private PageStatus status;
}
