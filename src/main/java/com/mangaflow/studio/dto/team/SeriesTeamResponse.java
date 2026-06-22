package com.mangaflow.studio.dto.team;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeriesTeamResponse {
    private Long seriesId;
    private String seriesTitle;
    private String coverColor;
    private String coverImageUrl;
    private TeamMember mangaka;
    private TeamMember tantouEditor;
    private List<TeamMember> assistants;
    private List<TeamMember> pendingInvites;
}
