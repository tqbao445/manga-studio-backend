package com.dacphu.manga.createseries_demo.requestDto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SeriesResponse {
    private Long id;

    private String title;

    private String synopsis;

    private String genre;

    private String status;

    private String mangakaName;

    private String tantouEditorName;

    private LocalDateTime createdAt;
}
