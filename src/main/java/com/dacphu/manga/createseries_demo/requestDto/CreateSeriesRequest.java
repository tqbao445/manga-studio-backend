package com.dacphu.manga.createseries_demo.requestDto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSeriesRequest {
    @NotBlank
    private String title;

    private String titleJp;

    @NotBlank
    private String synopsis;

    @NotBlank
    private String genre;

    private String targetDemographic;

    private String coverColor;

    private String publishFrequency;

    private Long tantouEditorId;
}

