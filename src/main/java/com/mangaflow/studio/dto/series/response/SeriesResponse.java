package com.mangaflow.studio.dto.series.response;

import com.mangaflow.studio.dto.auth.response.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class SeriesResponse {

    private Long id;
    private String title;
    private String titleJp;
    private String synopsis;
    private List<String> genres;
    private List<String> targetDemographics;
    private String status;
    private String coverColor;
    private String coverImageUrl;
    private UserDTO mangaka;
    private UserDTO tantouEditor;
    private Integer chapterCount;
    private Integer currentRank;
    private String currentTier;
    private String scheduleType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
