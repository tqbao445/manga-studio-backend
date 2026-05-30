package com.mangaflow.studio.dto.series.request;

import com.mangaflow.studio.model.series.SeriesStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStatusRequest {
    @NotNull(message = "Status is required")
    private SeriesStatus status;
}
