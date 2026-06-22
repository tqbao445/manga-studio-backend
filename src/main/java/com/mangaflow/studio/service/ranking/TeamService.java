package com.mangaflow.studio.service.ranking;

import com.mangaflow.studio.dto.team.SeriesTeamResponse;
import com.mangaflow.studio.dto.team.TeamMember;
import com.mangaflow.studio.dto.team.TeamOverviewResponse;
import com.mangaflow.studio.model.auth.Role;
import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.model.series.Series;
import com.mangaflow.studio.model.series.SeriesAssistant;
import com.mangaflow.studio.model.series.SeriesTantouInvitation;
import com.mangaflow.studio.repository.series.SeriesAssistantRepository;
import com.mangaflow.studio.repository.series.SeriesRepository;
import com.mangaflow.studio.repository.series.SeriesTantouInvitationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final SeriesRepository seriesRepository;
    private final SeriesAssistantRepository assistantRepository;
    private final SeriesTantouInvitationRepository tantouInvitationRepository;

    public TeamOverviewResponse getTeamOverview(User currentUser, String search) {
        List<Series> seriesList = resolveSeries(currentUser);
        List<SeriesTeamResponse> responses = seriesList.stream()
                .map(s -> buildSeriesTeam(s, currentUser, search))
                .filter(r -> r != null)
                .collect(Collectors.toList());
        return TeamOverviewResponse.builder().series(responses).build();
    }

    private List<Series> resolveSeries(User user) {
        if (user.getRole() == Role.MANGAKA) {
            return seriesRepository.findByMangakaId(user.getId());
        } else if (user.getRole() == Role.TANTOU_EDITOR) {
            return seriesRepository.findByTantouEditorId(user.getId());
        } else if (user.getRole() == Role.ASSISTANT) {
            return assistantRepository.findByAssistantIdAndStatus(user.getId(),
                            com.mangaflow.studio.model.series.InvitationStatus.ACCEPTED).stream()
                    .map(SeriesAssistant::getSeries)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private SeriesTeamResponse buildSeriesTeam(Series series, User currentUser, String search) {
        TeamMember mangaka = toMember(series.getMangaka(), "MANGAKA", "OWNER");

        TeamMember tantou = series.getTantouEditor() != null
                ? toMember(series.getTantouEditor(), "TANTOU_EDITOR", "ACCEPTED")
                : null;
        if (tantou != null && tantou.getId().equals(currentUser.getId())) {
            tantou = null;
        }

        List<SeriesAssistant> acceptedAssistants = assistantRepository
                .findBySeriesIdAndStatus(series.getId(),
                        com.mangaflow.studio.model.series.InvitationStatus.ACCEPTED);

        List<TeamMember> assistants = acceptedAssistants.stream()
                .map(sa -> toMember(sa.getAssistant(), "ASSISTANT", "ACCEPTED"))
                .filter(m -> !m.getId().equals(currentUser.getId()))
                .collect(Collectors.toList());

        List<SeriesAssistant> pendingAssistants = assistantRepository
                .findBySeriesIdAndStatus(series.getId(),
                        com.mangaflow.studio.model.series.InvitationStatus.PENDING);

        List<TeamMember> pendingInvites = pendingAssistants.stream()
                .map(sa -> toMember(sa.getAssistant(), "ASSISTANT", "PENDING"))
                .filter(m -> !m.getId().equals(currentUser.getId()))
                .collect(Collectors.toList());

        List<SeriesTantouInvitation> pendingTantous = tantouInvitationRepository
                .findBySeriesIdAndStatus(series.getId(),
                        com.mangaflow.studio.model.series.InvitationStatus.PENDING);

        List<TeamMember> tantouPendingInvites = pendingTantous.stream()
                .map(sti -> toMember(sti.getTantou(), "TANTOU_EDITOR", "PENDING"))
                .filter(m -> !m.getId().equals(currentUser.getId()))
                .collect(Collectors.toList());

        pendingInvites.addAll(tantouPendingInvites);

        SeriesTeamResponse response = SeriesTeamResponse.builder()
                .seriesId(series.getId())
                .seriesTitle(series.getTitle())
                .coverColor(series.getCoverColor())
                .coverImageUrl(series.getCoverImageUrl())
                .mangaka(mangaka)
                .tantouEditor(tantou)
                .assistants(assistants)
                .pendingInvites(pendingInvites)
                .build();

        if (search != null && !search.isBlank() && !matchesSearch(response, search)) {
            return null;
        }
        return response;
    }

    private boolean matchesSearch(SeriesTeamResponse response, String search) {
        String q = search.toLowerCase();
        if (response.getMangaka() != null && response.getMangaka().getDisplayName().toLowerCase().contains(q))
            return true;
        if (response.getTantouEditor() != null && response.getTantouEditor().getDisplayName().toLowerCase().contains(q))
            return true;
        for (TeamMember a : response.getAssistants()) {
            if (a.getDisplayName().toLowerCase().contains(q)) return true;
        }
        for (TeamMember p : response.getPendingInvites()) {
            if (p.getDisplayName().toLowerCase().contains(q)) return true;
        }
        return response.getSeriesTitle().toLowerCase().contains(q);
    }

    private TeamMember toMember(User user, String role, String status) {
        return TeamMember.builder()
                .id(user.getId())
                .displayName(user.getDisplayName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .role(role)
                .status(status)
                .build();
    }
}
