package com.mangaflow.studio.series.dto.response;

import com.mangaflow.studio.auth.dto.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class SeriesResponse {

    private Long id;
    private String title;
    private String titleJp;
    private String synopsis;
    private String genre;
    private String targetDemographic;
    private String status;
    private String coverColor;
    private String coverImageUrl;
    private Boolean isMature;
    private UserDTO mangaka;
    private UserDTO tantouEditor;
    private Integer chapterCount;
    private Integer currentRank;
    private String currentTier;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
