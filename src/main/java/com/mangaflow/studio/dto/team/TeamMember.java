package com.mangaflow.studio.dto.team;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMember {
    private Long id;
    private String displayName;
    private String email;
    private String avatarUrl;
    private String role;
    private String status;
}
