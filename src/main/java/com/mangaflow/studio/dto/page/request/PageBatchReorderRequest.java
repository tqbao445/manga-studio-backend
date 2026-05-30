package com.mangaflow.studio.dto.page.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Request để sắp xếp lại toàn bộ pages sau kéo thả")
public class PageBatchReorderRequest {

    @NotEmpty(message = "Page IDs list is required")
    @ArraySchema(
            schema = @Schema(description = "ID của page", example = "1"),
            arraySchema = @Schema(description = "Danh sách page IDs theo thứ tự mới. Phần tử đầu = pageNumber 1, phần tử thứ 2 = pageNumber 2, ...")
    )
    private List<Long> pageIds;
}
