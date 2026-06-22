package com.mangaflow.studio.controller.ranking;

import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.team.TeamOverviewResponse;
import com.mangaflow.studio.service.ranking.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/team")
@RequiredArgsConstructor
@Tag(name = "Team", description = "API quản lý team — xem thành viên theo series")
public class TeamController {

    private final TeamService teamService;

    @Operation(summary = "Get team overview",
            description = "Trả về danh sách series + team members (mangaka, tantou, assistants)"
                    + " của user hiện tại. MANGAKA thấy series của họ, TANTOU_EDITOR thấy series họ phụ trách.")
    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('MANGAKA', 'TANTOU_EDITOR', 'ASSISTANT')")
    public ResponseEntity<TeamOverviewResponse> getTeamOverview(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(teamService.getTeamOverview(currentUser.getUser(), search));
    }
}
