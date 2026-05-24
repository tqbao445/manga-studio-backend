package com.mangaflow.studio.series.dto.request;

import com.mangaflow.studio.series.enums.SeriesStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStatusRequest {
    @NotNull(message = "Status is required")
    private SeriesStatus status;
}
