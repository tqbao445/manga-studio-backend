package com.mangaflow.studio.service.dashboard;

import com.mangaflow.studio.common.exception.AppException;
import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.dashboard.response.*;
import com.mangaflow.studio.dto.meeting.MeetingResponse;
import com.mangaflow.studio.dto.ranking.response.AtRiskSeriesResponse;
import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.model.chapter.Chapter;
import com.mangaflow.studio.model.chapter.ChapterStatus;
import com.mangaflow.studio.model.meeting.MeetingParticipant;
import com.mangaflow.studio.model.meeting.MeetingStatus;
import com.mangaflow.studio.model.metric.SeriesPeriodMetric;
import com.mangaflow.studio.model.notification.Notification;
import com.mangaflow.studio.model.series.InvitationStatus;
import com.mangaflow.studio.model.series.Series;
import com.mangaflow.studio.model.series.SeriesAssistant;
import com.mangaflow.studio.model.series.SeriesStatus;
import com.mangaflow.studio.model.task.Task;
import com.mangaflow.studio.model.task.TaskStatus;
import com.mangaflow.studio.repository.auth.UserRepository;
import com.mangaflow.studio.repository.chapter.ChapterRepository;
import com.mangaflow.studio.repository.comment.CommentRepository;
import com.mangaflow.studio.repository.meeting.MeetingParticipantRepository;
import com.mangaflow.studio.repository.metric.SeriesPeriodMetricRepository;
import com.mangaflow.studio.repository.notification.NotificationRepository;
import com.mangaflow.studio.repository.schedule.PublicationScheduleRepository;
import com.mangaflow.studio.repository.series.SeriesAssistantRepository;
import com.mangaflow.studio.repository.series.SeriesRepository;
import com.mangaflow.studio.repository.task.TaskRepository;
import com.mangaflow.studio.service.meeting.MeetingService;
import com.mangaflow.studio.service.ranking.SeriesRankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    // ─── DI: Tất cả Repository và Service cần dùng ───
    private final SeriesRepository seriesRepository;
    private final ChapterRepository chapterRepository;
    private final TaskRepository taskRepository;
    private final SeriesAssistantRepository seriesAssistantRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final PublicationScheduleRepository scheduleRepository;
    private final SeriesPeriodMetricRepository metricRepository;
    private final MeetingParticipantRepository meetingParticipantRepository;
    private final CommentRepository commentRepository;
    private final SeriesRankingService rankingService;
    private final MeetingService meetingService;

    /**
     * ─── getStats() ───
     * Điều hướng theo role của user đang login.
     * Mỗi role trả về 1 DTO khác nhau.
     */
    public Object getStats(CustomUserDetails user) {
        String role = user.getRole();
        return switch (role) {
            case "MANGAKA" -> buildMangakaStats(user);
            case "ASSISTANT" -> buildAssistantStats(user);
            case "TANTOU_EDITOR" -> buildEditorStats(user);
            case "EDITORIAL_BOARD", "CHIEF_EDITOR" -> buildBoardStats(user);
            default -> throw new AppException(HttpStatus.FORBIDDEN,
                    "Role '" + role + "' does not have dashboard access");
        };
    }

    // ════════════════════════════════════════════════════════════
    //  BUILD MANGAKA STATS
    // ════════════════════════════════════════════════════════════
    private MangakaStatsResponse buildMangakaStats(CustomUserDetails user) {
        Long userId = user.getUserId();

        // Bước 1: Lấy tất cả series của mangaka
        List<Series> mySeries = seriesRepository.findByMangakaId(userId);

        // Bước 2: Lọc series đang hoạt động (ONGOING, AT_RISK, HIATUS)
        List<Series> activeSeries = mySeries.stream()
                .filter(s -> s.getStatus() == SeriesStatus.ONGOING
                        || s.getStatus() == SeriesStatus.AT_RISK
                        || s.getStatus() == SeriesStatus.HIATUS)
                .collect(Collectors.toList());

        // Bước 3: Đếm chapter IN_PROGRESS của các series active
        long ongoingChapters = 0;
        for (Series series : activeSeries) {
            ongoingChapters += chapterRepository.countBySeriesIdAndStatus(
                    series.getId(), ChapterStatus.IN_PROGRESS);
        }

        // Bước 4: Đếm task của mangaka (assignedBy = userId)
        List<Task> myAssignedTasks = taskRepository.findByAssignedById(userId);
        long pendingTasks = myAssignedTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.TODO
                        || t.getStatus() == TaskStatus.IN_PROGRESS)
                .count();
        long submissionsToReview = myAssignedTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.SUBMITTED)
                .count();

        // Bước 5: Lấy upcoming deadlines (trong 14 ngày tới)
        LocalDate today = LocalDate.now();
        LocalDate twoWeeksLater = today.plusDays(14);
        List<ChapterStatus> deadlineStatuses = List.of(
                ChapterStatus.IN_PROGRESS, ChapterStatus.PLANNED, ChapterStatus.SUBMITTED
        );

        List<UpcomingDeadlineResponse> deadlines = new ArrayList<>();
        for (Series series : activeSeries) {
            List<Chapter> chapters = chapterRepository
                    .findBySeriesIdAndStatusInAndDeadlineBetween(
                            series.getId(), deadlineStatuses, today, twoWeeksLater);
            for (Chapter ch : chapters) {
                deadlines.add(UpcomingDeadlineResponse.builder()
                        .chapterId(ch.getId())
                        .title("Ch." + ch.getChapterNumber()
                                + (ch.getTitle() != null ? " - " + ch.getTitle() : ""))
                        .deadline(ch.getDeadline() != null ? ch.getDeadline().toString() : "")
                        .daysLeft(ch.getDeadline() != null
                                ? ChronoUnit.DAYS.between(today, ch.getDeadline())
                                : 0)
                        .build());
            }
        }

        // Bước 6: Tính rank và trend (từ series có dữ liệu ranking gần nhất)
        Integer currentRank = null;
        String rankTrend = "NEW";
        // Lấy series đầu tiên có currentRank để hiển thị
        for (Series series : activeSeries) {
            if (series.getCurrentRank() != null) {
                currentRank = series.getCurrentRank();
                // Tính trend từ lịch sử ranking
                List<SeriesPeriodMetric> history = metricRepository
                        .findTop2BySeriesIdOrderByPeriodLabelDesc(series.getId());
                if (history.size() >= 2) {
                    int prevRank = history.get(1).getRank() != null
                            ? history.get(1).getRank() : 0;
                    int curRank = history.get(0).getRank() != null
                            ? history.get(0).getRank() : 0;
                    if (prevRank > curRank) rankTrend = "UP";
                    else if (prevRank < curRank) rankTrend = "DOWN";
                    else rankTrend = "SAME";
                }
                break;
            }
        }

        return MangakaStatsResponse.builder()
                .activeSeries(activeSeries.size())
                .ongoingChapters(ongoingChapters)
                .pendingTasks(pendingTasks)
                .submissionsToReview(submissionsToReview)
                .upcomingDeadlines(deadlines)
                .currentRank(currentRank)
                .rankTrend(rankTrend)
                .build();
    }

    // ════════════════════════════════════════════════════════════
    //  BUILD ASSISTANT STATS
    // ════════════════════════════════════════════════════════════
    private AssistantStatsResponse buildAssistantStats(CustomUserDetails user) {
        Long userId = user.getUserId();

        // Bước 1: Lấy danh sách series assistant đã ACCEPTED
        List<SeriesAssistant> assistantships = seriesAssistantRepository
                .findByAssistantIdAndStatus(userId, InvitationStatus.ACCEPTED);

        // Bước 2: Map sang SeriesBasicDTO
        List<SeriesBasicDTO> assignedSeries = assistantships.stream()
                .map(sa -> {
                    Series series = sa.getSeries();
                    return SeriesBasicDTO.builder()
                            .id(series.getId())
                            .title(series.getTitle())
                            .status(series.getStatus().name())
                            .coverImageUrl(series.getCoverImageUrl())
                            .coverColor(series.getCoverColor())
                            .build();
                })
                .collect(Collectors.toList());

        // Bước 3: Lấy task của assistant và đếm theo trạng thái
        List<Task> myTasks = taskRepository.findByAssistantId(userId);
        long todo = myTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.TODO).count();
        long inProgress = myTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).count();
        long done = myTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.DONE).count();

        return AssistantStatsResponse.builder()
                .myTasks(myTasks.size())
                .inProgress(inProgress)
                .todo(todo)
                .done(done)
                .assignedSeries(assignedSeries)
                .build();
    }

    // ════════════════════════════════════════════════════════════
    //  BUILD TANTOU_EDITOR STATS
    // ════════════════════════════════════════════════════════════
    private EditorStatsResponse buildEditorStats(CustomUserDetails user) {
        Long userId = user.getUserId();

        // Bước 1: Lấy series được phân công (tantouEditor = userId)
        List<Series> mySeries = seriesRepository.findByTantouEditorId(userId);

        // Bước 2: Map sang SeriesBasicDTO
        List<SeriesBasicDTO> assignedSeriesList = mySeries.stream()
                .map(s -> SeriesBasicDTO.builder()
                        .id(s.getId())
                        .title(s.getTitle())
                        .status(s.getStatus().name())
                        .coverImageUrl(s.getCoverImageUrl())
                        .coverColor(s.getCoverColor())
                        .build())
                .collect(Collectors.toList());

        // Bước 3: Lấy chapter IN_REVIEW của các series
        List<Chapter> chaptersInReview = chapterRepository
                .findByTantouEditorIdAndStatus(userId, ChapterStatus.IN_REVIEW);

        List<ChapterBasicDTO> chaptersInReviewList = chaptersInReview.stream()
                .map(ch -> ChapterBasicDTO.builder()
                        .id(ch.getId())
                        .seriesId(ch.getSeries().getId())
                        .seriesTitle(ch.getSeries().getTitle())
                        .chapterNumber(ch.getChapterNumber())
                        .title(ch.getTitle())
                        .status(ch.getStatus().name())
                        .build())
                .collect(Collectors.toList());

        // Bước 4: Lấy publication queue (lịch xuất bản từ schedule)
        List<PublicationQueueItem> queue = new ArrayList<>();
        for (Series series : mySeries) {
            scheduleRepository.findBySeriesIdAndStatus(series.getId(),
                    com.mangaflow.studio.model.schedule.ScheduleStatus.ACTIVE)
                    .ifPresent(schedule -> queue.add(PublicationQueueItem.builder()
                            .seriesId(series.getId())
                            .seriesTitle(series.getTitle())
                            .chapterNumber(schedule.getNextChapterNumber())
                            .scheduledDate(schedule.getStartDate().toString())
                            .build()));
        }

        // Bước 5: Đếm pending comments (comments ACTIVE chưa RESOLVED)
        // Dùng CommentRepository để đếm — cần JOIN qua Page → Chapter → Series
        long pendingComments = 0;
        // Note: CommentRepository không có method đếm theo series,
        // ta sẽ bỏ qua hoặc implement sau (đây là việc cần cải thiện)

        return EditorStatsResponse.builder()
                .assignedSeries(mySeries.size())
                .chaptersInReview(chaptersInReview.size())
                .pendingComments(pendingComments)
                .assignedSeriesList(assignedSeriesList)
                .chaptersInReviewList(chaptersInReviewList)
                .publicationQueue(queue)
                .build();
    }

    // ════════════════════════════════════════════════════════════
    //  BUILD EDITORIAL_BOARD / CHIEF_EDITOR STATS
    // ════════════════════════════════════════════════════════════
    private BoardStatsResponse buildBoardStats(CustomUserDetails user) {
        Long userId = user.getUserId();

        // Bước 1: Đếm totalActiveSeries (ONGOING + AT_RISK)
        List<Series> activeSeries = seriesRepository.findByStatusIn(
                List.of(SeriesStatus.ONGOING, SeriesStatus.AT_RISK));

        // Bước 2: Đếm proposalsPending (PENDING_TANTOU + PENDING_BOARD_VOTE)
        List<Series> pendingProposals = seriesRepository.findByStatusIn(
                List.of(SeriesStatus.PENDING_TANTOU, SeriesStatus.PENDING_BOARD_VOTE));

        // Bước 3: Đếm chaptersPending (SUBMITTED + IN_REVIEW)
        long chaptersPending = chapterRepository.countByStatusIn(
                List.of(ChapterStatus.SUBMITTED, ChapterStatus.IN_REVIEW));

        // Bước 4: Lấy at-risk series (dùng service có sẵn)
        List<AtRiskSeriesResponse> atRiskSeries = rankingService.getAtRiskSeries();

        // Bước 5: Lấy upcoming meetings của user
        List<MeetingParticipant> myMeetings = meetingParticipantRepository.findByUserId(userId);
        List<MeetingResponse> upcomingMeetings = myMeetings.stream()
                .map(mp -> mp.getMeeting())
                .filter(m -> m.getStatus() == MeetingStatus.PENDING
                        || m.getStatus() == MeetingStatus.IN_PROGRESS)
                .map(meeting -> meetingService.getMeeting(meeting.getId()))
                .collect(Collectors.toList());

        return BoardStatsResponse.builder()
                .totalActiveSeries(activeSeries.size())
                .proposalsPending(pendingProposals.size())
                .chaptersPending(chaptersPending)
                .atRiskSeries(atRiskSeries)
                .upcomingMeetings(upcomingMeetings)
                .build();
    }

    // ════════════════════════════════════════════════════════════
    //  GET ACTIVITY FEED
    // ════════════════════════════════════════════════════════════

    /**
     * Lấy 20 activity gần đây nhất từ Notification table.
     * Map Notification → ActivityFeedResponse (kèm userName).
     */
    @Transactional(readOnly = true)
    public List<ActivityFeedResponse> getActivityFeed() {
        // Lấy 20 notification gần đây nhất
        List<Notification> notifications = notificationRepository
                .findTop20ByOrderByCreatedAtDesc();

        return notifications.stream().map(n -> {
            // Tìm thông tin user từ userId
            String userName = "Unknown";
            if (n.getUserId() != null) {
                userName = userRepository.findById(n.getUserId())
                        .map(u -> u.getDisplayName() != null
                                ? u.getDisplayName() : u.getUsername())
                        .orElse("Unknown");
            }

            return ActivityFeedResponse.builder()
                    .id(n.getId())
                    .userName(userName)
                    .message(n.getMessage() != null ? n.getMessage() : n.getTitle())
                    .type(n.getType())
                    .createdAt(n.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }
}
