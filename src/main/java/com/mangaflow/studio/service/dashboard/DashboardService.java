package com.mangaflow.studio.service.dashboard;

import com.mangaflow.studio.common.exception.AppException;
import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.dashboard.request.NudgeRequest;
import com.mangaflow.studio.dto.dashboard.response.*;
import com.mangaflow.studio.model.auth.Role;
import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.model.chapter.Chapter;
import com.mangaflow.studio.model.chapter.ChapterStatus;
import com.mangaflow.studio.model.metric.SeriesPeriodMetric;
import com.mangaflow.studio.model.series.Series;
import com.mangaflow.studio.model.series.SeriesStatus;
import com.mangaflow.studio.model.task.Priority;
import com.mangaflow.studio.model.task.Task;
import com.mangaflow.studio.model.task.TaskStatus;
import com.mangaflow.studio.model.task.TaskSubmissionStatus;
import com.mangaflow.studio.repository.auth.UserRepository;
import com.mangaflow.studio.repository.chapter.ChapterRepository;
import com.mangaflow.studio.repository.metric.SeriesPeriodMetricRepository;
import com.mangaflow.studio.repository.series.SeriesRepository;
import com.mangaflow.studio.repository.task.TaskRepository;
import com.mangaflow.studio.repository.task.TaskSubmissionRepository;
import com.mangaflow.studio.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ── DashboardService ──
 * Service xử lý toàn bộ logic nghiệp vụ cho Dashboard module.
 * <p>
 * ══════════════════════════════════════════════════════════════════
 *  Các chức năng:
 * ══════════════════════════════════════════════════════════════════
 *  1. getStats()         → GET  /api/v1/dashboard/stats
 *     - MANGAKA: activeSeries, ongoingChapters, pendingTasks, ...
 *     - TANTOU_EDITOR: assignedSeries, chaptersInReview, lateStudios
 *  2. getEarnings()      → GET  /api/v1/dashboard/earnings
 *     - ASSISTANT: thu nhập theo tuần/tháng từ task DONE
 *  3. sendNudge()        → POST /api/v1/dashboard/nudge/{authorId}
 *     - TANTOU_EDITOR: gửi notification nhắc Mangaka
 * <p>
 * 📌 @Transactional(readOnly = true): Mặc định read-only,
 *    method sendNudge() override bằng @Transactional riêng.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    // ─── Repositories ───
    private final SeriesRepository seriesRepository;
    private final ChapterRepository chapterRepository;
    private final TaskRepository taskRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final SeriesPeriodMetricRepository metricRepository;
    private final UserRepository userRepository;

    // ─── Services ───
    private final NotificationService notificationService;

    // ════════════════════════════════════════════════════════════════
    // 1. GET /api/v1/dashboard/stats — Dashboard tổng quan
    // ════════════════════════════════════════════════════════════════
    //
    // Mục đích:
    //   Trả về số liệu tổng quan dashboard cho MANGAKA hoặc TANTOU_EDITOR.
    //
    // Cách hoạt động:
    //   - Xác định role từ JWT
    //   - Nếu MANGAKA → tính các chỉ số series/chapter/task/rank
    //   - Nếu TANTOU_EDITOR → tính assigned series, review queue, late alerts
    //   - Role khác → throw 403 Forbidden
    //
    // ════════════════════════════════════════════════════════════════
    public MangakaStatsResponse getStats(CustomUserDetails user) {
        String role = user.getRole();
        Long userId = user.getUserId();

        switch (role) {
            case "MANGAKA":
                return buildMangakaStats(userId);
            case "TANTOU_EDITOR":
                return buildTantouStats(userId);
            default:
                throw new AppException(HttpStatus.FORBIDDEN,
                        "Dashboard stats not available for role: " + role);
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Helper: Build stats cho MANGAKA
    // ────────────────────────────────────────────────────────────────

    /**
     * Xây dựng stats cho MANGAKA dashboard.
     *
     * @param mangakaId ID của Mangaka đang đăng nhập
     * @return MangakaStatsResponse với các chỉ số cho Mangaka
     */
    private MangakaStatsResponse buildMangakaStats(Long mangakaId) {
        // ── 1. Lấy tất cả series của Mangaka ──
        List<Series> mySeries = seriesRepository.findByMangakaId(mangakaId);
        List<Long> seriesIds = mySeries.stream()
                .map(Series::getId)
                .toList();

        // ── 2. activeSeries: series đang ONGOING hoặc AT_RISK ──
        long activeSeries = mySeries.stream()
                .filter(s -> s.getStatus() == SeriesStatus.ONGOING
                        || s.getStatus() == SeriesStatus.AT_RISK)
                .count();

        // ── 3. ongoingChapters: chapter của series đang làm ──
        long ongoingChapters = 0;
        List<Chapter> allChapters = new ArrayList<>();
        for (Long sid : seriesIds) {
            List<Chapter> chapters = chapterRepository
                    .findBySeriesIdOrderByChapterNumberAsc(sid);
            allChapters.addAll(chapters);
            ongoingChapters += chapters.stream()
                    .filter(c -> c.getStatus() == ChapterStatus.IN_PROGRESS
                            || c.getStatus() == ChapterStatus.SUBMITTED
                            || c.getStatus() == ChapterStatus.IN_REVIEW)
                    .count();
        }

        // ── 4. pendingTasks: task do Mangaka giao, đang TODO hoặc IN_PROGRESS ──
        // Dùng Specification tương tự TaskService — ở đây query đơn giản hơn
        long pendingTasks = taskRepository.findAll().stream()
                .filter(t -> t.getAssignedBy().getId().equals(mangakaId))
                .filter(t -> t.getStatus() == TaskStatus.TODO
                        || t.getStatus() == TaskStatus.IN_PROGRESS)
                .count();

        // ── 5. submissionsToReview: task có submission SUBMITTED ──
        long submissionsToReview = taskRepository.findAll().stream()
                .filter(t -> t.getAssignedBy().getId().equals(mangakaId))
                .filter(t -> t.getSubmissions().stream()
                        .anyMatch(s -> s.getStatus() == TaskSubmissionStatus.SUBMITTED))
                .count();

        // ── 6. upcomingDeadlines: chapter deadline sắp tới (limit 5) ──
        LocalDateTime now = LocalDateTime.now();
        List<DeadlineItem> deadlines = allChapters.stream()
                .filter(c -> c.getDeadline() != null)
                .filter(c -> c.getDeadline().atStartOfDay().isAfter(now))
                .sorted(Comparator.comparing(c -> c.getDeadline()))
                .limit(5)
                .map(c -> DeadlineItem.builder()
                        .chapterId(c.getId())
                        .seriesId(c.getSeries().getId())
                        .seriesTitle(c.getSeries().getTitle())
                        .chapterNumber(c.getChapterNumber())
                        .title(c.getTitle())
                        .deadline(c.getDeadline().atStartOfDay())
                        .daysLeft(ChronoUnit.DAYS.between(
                                now.toLocalDate(), c.getDeadline()))
                        .build())
                .toList();

        // ── 7. currentRank + rankTrend ──
        // Lấy từ metric gần nhất của series đầu tiên (hoặc series có rank tốt nhất)
        Integer currentRank = null;
        String rankTrend = "FLAT";

        if (!seriesIds.isEmpty()) {
            // Tìm metric gần nhất của tất cả series, lấy rank của series có rank cao nhất
            List<SeriesPeriodMetric> latestMetrics = new ArrayList<>();
            for (Long sid : seriesIds) {
                List<SeriesPeriodMetric> metrics = metricRepository
                        .findTop2BySeriesIdOrderByPeriodLabelDesc(sid);
                if (!metrics.isEmpty()) {
                    latestMetrics.add(metrics.get(0));
                }
            }

            if (!latestMetrics.isEmpty()) {
                // Lấy series có rank tốt nhất (nhỏ nhất)
                SeriesPeriodMetric best = latestMetrics.stream()
                        .min(Comparator.comparing(SeriesPeriodMetric::getRank))
                        .orElse(null);

                if (best != null) {
                    currentRank = best.getRank();

                    // Tính trend: so sánh với kỳ trước
                    List<SeriesPeriodMetric> history = metricRepository
                            .findTop2BySeriesIdOrderByPeriodLabelDesc(
                                    best.getSeries().getId());
                    if (history.size() >= 2) {
                        int prevRank = history.get(1).getRank();
                        if (best.getRank() < prevRank) {
                            rankTrend = "UP";
                        } else if (best.getRank() > prevRank) {
                            rankTrend = "DOWN";
                        } else {
                            rankTrend = "FLAT";
                        }
                    }
                }
            }
        }

        // ── Build response ──
        return MangakaStatsResponse.builder()
                .activeSeries(activeSeries)
                .ongoingChapters(ongoingChapters)
                .pendingTasks(pendingTasks)
                .submissionsToReview(submissionsToReview)
                .upcomingDeadlines(deadlines)
                .currentRank(currentRank)
                .rankTrend(rankTrend)
                .build();
    }

    // ────────────────────────────────────────────────────────────────
    // Helper: Build stats cho TANTOU_EDITOR
    // ────────────────────────────────────────────────────────────────

    /**
     * Xây dựng stats cho TANTOU_EDITOR dashboard.
     *
     * @param tantouId ID của Tantou Editor đang đăng nhập
     * @return MangakaStatsResponse với các chỉ số cho Tantou
     */
    private MangakaStatsResponse buildTantouStats(Long tantouId) {
        // ── 1. Lấy tất cả series được gán cho Tantou ──
        List<Series> assignedSeries = seriesRepository
                .findByTantouEditorId(tantouId);
        List<Long> seriesIds = assignedSeries.stream()
                .map(Series::getId)
                .toList();

        // ── 2. assignedSeries: số series được gán ──
        long seriesCount = assignedSeries.size();

        // ── 3. chaptersInReviewList: chapter IN_REVIEW của các series ──
        List<ChapterInReviewItem> chaptersInReview = new ArrayList<>();
        List<LateStudioAlertItem> lateStudios = new ArrayList<>();
        long pendingComments = 0;

        for (Long sid : seriesIds) {
            List<Chapter> chapters = chapterRepository
                    .findBySeriesIdOrderByChapterNumberAsc(sid);

            for (Chapter ch : chapters) {
                // Chapters đang IN_REVIEW → Manuscript Review Queue
                if (ch.getStatus() == ChapterStatus.IN_REVIEW) {
                    chaptersInReview.add(ChapterInReviewItem.builder()
                            .id(ch.getId())
                            .chapterNumber(ch.getChapterNumber())
                            .title(ch.getTitle())
                            .seriesTitle(ch.getSeries().getTitle())
                            .submittedAt(ch.getUpdatedAt())
                            .pageCount(ch.getPageCount())
                            .build());
                }

                // Late Studios Alert: progress < 50 AND deadline - now <= 3 days
                if (ch.getDeadline() != null && ch.getProgressPercent() != null) {
                    long daysLeft = ChronoUnit.DAYS.between(
                            LocalDate.now(), ch.getDeadline());
                    if (ch.getProgressPercent() < 50 && daysLeft <= 3 && daysLeft >= 0) {
                        User mangaka = ch.getSeries().getMangaka();
                        lateStudios.add(LateStudioAlertItem.builder()
                                .authorId(mangaka.getId())
                                .authorName(mangaka.getDisplayName())
                                .seriesId(sid)
                                .seriesTitle(ch.getSeries().getTitle())
                                .chapterId(ch.getId())
                                .chapterTitle(ch.getTitle())
                                .chapterNumber(ch.getChapterNumber())
                                .progressPercent(ch.getProgressPercent())
                                .deadline(ch.getDeadline())
                                .daysLeft(daysLeft)
                                .build());
                    }
                }

                // Tạm tính pendingComments = số chapter IN_REVIEW
                if (ch.getStatus() == ChapterStatus.IN_REVIEW
                        || ch.getStatus() == ChapterStatus.SUBMITTED) {
                    pendingComments++;
                }
            }
        }

        return MangakaStatsResponse.builder()
                .assignedSeries(seriesCount)
                .pendingComments(pendingComments)
                .chaptersInReviewList(chaptersInReview)
                .lateStudiosAlert(lateStudios)
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    // 2. GET /api/v1/dashboard/earnings — Bảng kê thu nhập
    // ════════════════════════════════════════════════════════════════
    //
    // Mục đích:
    //   Trả về biểu đồ thu nhập của ASSISTANT theo tuần hoặc tháng.
    //
    // Cách hoạt động:
    //   1. Lấy userId từ JWT
    //   2. Query tất cả task DONE do ASSISTANT đó làm
    //   3. Tính amount cho mỗi task dựa trên priority
    //   4. Group theo period (week/month)
    //   5. Trả về mảng { label, period, amount, taskCount }
    //
    // 📌 Quy tắc tính amount (tạm hardcode — sau này có thể cấu hình):
    //    LOW    = 10,000 VND
    //    MEDIUM = 20,000 VND
    //    HIGH   = 35,000 VND
    //    URGENT = 50,000 VND
    //
    // ════════════════════════════════════════════════════════════════
    public List<EarningsResponse> getEarnings(String groupBy, CustomUserDetails user) {
        Long userId = user.getUserId();

        // ── Query tất cả task DONE của ASSISTANT ──
        List<Task> doneTasks = taskRepository.findAll().stream()
                .filter(t -> t.getAssistant() != null
                        && t.getAssistant().getId().equals(userId))
                .filter(t -> t.getStatus() == TaskStatus.DONE)
                .filter(t -> t.getAssignedAt() != null)
                .toList();

        // ── Group theo period ──
        // Map<periodString, List<Task>>
        Map<String, List<Task>> grouped;
        if ("month".equalsIgnoreCase(groupBy)) {
            // Group theo tháng: "2026-06"
            grouped = doneTasks.stream()
                    .collect(Collectors.groupingBy(t -> {
                        LocalDateTime dt = t.getAssignedAt();
                        return dt.getYear() + "-"
                                + String.format("%02d", dt.getMonthValue());
                    }));
        } else {
            // Mặc định group theo tuần (ISO week): "2026-W25"
            grouped = doneTasks.stream()
                    .collect(Collectors.groupingBy(t -> {
                        LocalDateTime dt = t.getAssignedAt();
                        int week = dt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                        int year = dt.get(IsoFields.WEEK_BASED_YEAR);
                        return year + "-W" + String.format("%02d", week);
                    }));
        }

        // ── Build response ──
        List<EarningsResponse> result = new ArrayList<>();
        int counter = 1;

        // Sort periods để trả về đúng thứ tự thời gian
        List<String> sortedPeriods = new ArrayList<>(grouped.keySet());
        Collections.sort(sortedPeriods);

        for (String period : sortedPeriods) {
            List<Task> tasksInPeriod = grouped.get(period);
            long totalAmount = tasksInPeriod.stream()
                    .mapToLong(t -> getAmountByPriority(t.getPriority()))
                    .sum();
            long taskCount = tasksInPeriod.size();

            result.add(EarningsResponse.builder()
                    .label("W" + counter)
                    .period(period)
                    .amount(totalAmount)
                    .taskCount(taskCount)
                    .build());
            counter++;
        }

        return result;
    }

    /**
     * Tính amount dựa trên priority của task.
     * Đây là mapping tạm thời — có thể đưa vào config sau.
     */
    private long getAmountByPriority(Priority priority) {
        if (priority == null) return 0;
        return switch (priority) {
            case LOW -> 10_000;
            case MEDIUM -> 20_000;
            case HIGH -> 35_000;
            case URGENT -> 50_000;
        };
    }

    // ════════════════════════════════════════════════════════════════
    // 3. POST /api/v1/dashboard/nudge/{authorId} — Nhắc nhở tác giả
    // ════════════════════════════════════════════════════════════════
    //
    // Mục đích:
    //   Tantou Editor gửi thông báo nhắc Mangaka về chapter sắp trễ.
    //
    // Cách hoạt động:
    //   1. Kiểm tra người gửi có role TANTOU_EDITOR
    //   2. Kiểm tra authorId tồn tại và có role MANGAKA
    //   3. Kiểm tra chapterId tồn tại và thuộc series của Tantou
    //   4. Tạo Notification qua NotificationService.createAndSend()
    //   5. Trả về 200 OK
    //
    // ════════════════════════════════════════════════════════════════
    @Transactional
    public void sendNudge(Long authorId, NudgeRequest request, CustomUserDetails user) {
        Long tantouId = user.getUserId();

        // ── Bước 1: Kiểm tra role ──
        if (!"TANTOU_EDITOR".equals(user.getRole())) {
            throw new AppException(HttpStatus.FORBIDDEN,
                    "Only TANTOU_EDITOR can send nudges");
        }

        // ── Bước 2: Kiểm tra author tồn tại và là MANGAKA ──
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "User not found: " + authorId));
        if (author.getRole() != Role.MANGAKA) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "User " + authorId + " is not a MANGAKA");
        }

        // ── Bước 3: Kiểm tra chapter tồn tại và thuộc series của Tantou ──
        Chapter chapter = chapterRepository.findById(request.getChapterId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Chapter not found: " + request.getChapterId()));

        // Kiểm tra Tantou có phải editor của series này không
        Series series = chapter.getSeries();
        if (series.getTantouEditor() == null
                || !series.getTantouEditor().getId().equals(tantouId)) {
            throw new AppException(HttpStatus.FORBIDDEN,
                    "You are not the Tantou Editor for this series");
        }

        // ── Bước 4: Gửi notification ──
        notificationService.createAndSend(
                authorId,
                "NUDGE",
                "Deadline reminder from " + user.getDisplayName(),
                request.getMessage(),
                "CHAPTER",
                request.getChapterId()
        );
    }
}
