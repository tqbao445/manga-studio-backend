package com.mangaflow.studio.service.schedule;

import com.mangaflow.studio.model.auth.Role;
import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.model.chapter.Chapter;
import com.mangaflow.studio.model.chapter.ChapterStatus;
import com.mangaflow.studio.model.schedule.PublicationSchedule;
import com.mangaflow.studio.model.schedule.ScheduleStatus;
import com.mangaflow.studio.model.schedule.ScheduleType;
import com.mangaflow.studio.model.series.Series;
import com.mangaflow.studio.model.series.SeriesStatus;
import com.mangaflow.studio.repository.auth.UserRepository;
import com.mangaflow.studio.repository.chapter.ChapterRepository;
import com.mangaflow.studio.repository.schedule.PublicationScheduleRepository;
import com.mangaflow.studio.repository.series.SeriesRepository;
import com.mangaflow.studio.service.common.WebSocketService;
import com.mangaflow.studio.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * ── AutoPublishService ──
 * Cron job tự động quét và xử lý lịch phát hành series.
 * <p>
 * ═══════════════════════════════════════════════════
 * Chạy: 8h sáng mỗi ngày (@Scheduled)
 * Công việc:
 * 1. Lấy tất cả schedule ACTIVE
 * 2. Với mỗi schedule, kiểm tra hôm nay có đúng lịch không
 * 3. Nếu đúng → tìm chapter theo nextChapterNumber
 * 4. Xử lý 3 case: APPROVED (publish), chưa APPROVED (miss), chưa tạo (miss)
 * 5. Nếu missCount >= 3 → đề xuất hủy + PAUSED
 * ═══════════════════════════════════════════════════
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoPublishService {

    private final PublicationScheduleRepository scheduleRepository;
    private final SeriesRepository seriesRepository;
    private final ChapterRepository chapterRepository;
    private final UserRepository userRepository;
    private final WebSocketService webSocketService;
    private final NotificationService notificationService;

    /**
     * processWeeklySchedules: Cron job chạy 8h sáng mỗi ngày.
     * <p>
     * 📌 @Scheduled(cron = "0 0 8 * * *"):
     * - 0: giây 0
     * - 0: phút 0
     * - 8: giờ 8 (sáng)
     * - *: mọi ngày trong tháng
     * - *: mọi tháng
     * - *: mọi ngày trong tuần
     */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void processWeeklySchedules() {
        LocalDate today = LocalDate.now();
        log.info("=== AutoPublishService: running for date {} ===", today);

        // Bước 1: Lấy tất cả schedule ACTIVE
        List<PublicationSchedule> activeSchedules = scheduleRepository
                .findByStatus(ScheduleStatus.ACTIVE);

        log.info("Found {} active schedules to process", activeSchedules.size());

        // Bước 2: Xử lý từng schedule
        for (PublicationSchedule schedule : activeSchedules) {
            processSchedule(schedule, today);
        }
    }

    /**
     * processSchedule: Xử lý 1 schedule cụ thể.
     * <p>
     * 📌 Luồng:
     * 1. Kiểm tra series còn ONGOING không
     * 2. Kiểm tra hôm nay có đúng lịch không
     * 3. Tìm chapter theo nextChapterNumber
     * 4. CASE 1: APPROVED → publish
     * 5. CASE 2: tồn tại nhưng chưa APPROVED → miss
     * 6. CASE 3: chưa tồn tại → miss
     * 7. Kiểm tra ngưỡng miss >= 3
     *
     * @param schedule schedule cần xử lý
     * @param today    ngày hiện tại
     */
    private void processSchedule(PublicationSchedule schedule, LocalDate today) {
        // Bước 1: Kiểm tra series tồn tại và ONGOING
        Series series = seriesRepository.findById(schedule.getSeries().getId()).orElse(null);
        if (series == null || series.getStatus() != SeriesStatus.ONGOING) {
            log.warn("Schedule #{}: series not found or not ONGOING, skipping", schedule.getId());
            return;
        }

        // Bước 2: Kiểm tra hôm nay có đúng lịch không
        if (!isDueToday(schedule, today)) {
            return; // Không đúng lịch → bỏ qua
        }

        Integer nextChNumber = schedule.getNextChapterNumber();
        log.info("Processing schedule #{} for series '{}' - next chapter: {}",
                schedule.getId(), series.getTitle(), nextChNumber);

        // Bước 3: Tìm chapter
        Chapter chapter = chapterRepository
                .findBySeriesIdAndChapterNumber(series.getId(), nextChNumber)
                .orElse(null);

        // Bước 4-6: Xử lý theo case
        if (chapter != null && chapter.getStatus() == ChapterStatus.APPROVED) {
            handleApprovedChapter(chapter, schedule, series);
        } else if (chapter != null) {
            handleUnapprovedChapter(chapter, schedule, series, nextChNumber);
        } else {
            handleMissingChapter(schedule, series, nextChNumber);
        }

        // Bước 7: Kiểm tra ngưỡng miss
        checkMissThreshold(schedule, series);
    }

    /**
     * isDueToday: Kiểm tra hôm nay có đúng ngày publish theo lịch không.
     * <p>
     * ═══════════════════════════════════════════════════
     * WEEKLY:  so sánh dayOfWeek với thứ hôm nay (1=Mon..7=Sun)
     * MONTHLY: so sánh dayOfMonth với ngày hôm nay
     * Edge case: nếu dayOfMonth > số ngày thực tế → lấy ngày cuối tháng
     * VD: dayOfMonth=31, tháng 2 có 28 ngày → chạy vào ngày 28
     * ═══════════════════════════════════════════════════
     *
     * @param schedule schedule cần kiểm tra
     * @param today    ngày hiện tại
     * @return true nếu hôm nay đúng lịch, false nếu không
     */
    private boolean isDueToday(PublicationSchedule schedule, LocalDate today) {
        if (schedule.getScheduleType() == ScheduleType.WEEKLY) {
            int todayDayOfWeek = today.getDayOfWeek().getValue(); // 1=Mon..7=Sun
            return todayDayOfWeek == schedule.getDayOfWeek();
        } else {
            // MONTHLY: xử lý edge case ngày cuối tháng
            int targetDay = schedule.getDayOfMonth();
            int lastDayOfMonth = YearMonth.from(today).lengthOfMonth();
            int effectiveDay = Math.min(targetDay, lastDayOfMonth);
            return today.getDayOfMonth() == effectiveDay;
        }
    }

    /**
     * handleApprovedChapter: CASE 1 — Chapter tồn tại và đã APPROVED.
     * <p>
     * Hành động:
     * - Set chapter status = PUBLISHED, publishDate = now
     * - Tăng nextChapterNumber lên 1
     * - Reset missCount = 0
     * - Gửi notification + websocket cho mangaka và tantou
     */
    private void handleApprovedChapter(Chapter chapter, PublicationSchedule schedule, Series series) {
        // Publish chapter
        chapter.setStatus(ChapterStatus.PUBLISHED);
        chapter.setPublishDate(LocalDateTime.now());
        chapterRepository.save(chapter);

        // Cập nhật schedule
        schedule.setNextChapterNumber(schedule.getNextChapterNumber() + 1);
        schedule.setMissCount(0);
        scheduleRepository.save(schedule);

        // Gửi thông báo
        String message = "Ch." + chapter.getChapterNumber()
                + " of \"" + series.getTitle() + "\" has been published";

        notificationService.createAndSend(
                series.getMangaka().getId(),
                "CHAPTER_PUBLISHED",
                "Chapter published on schedule",
                "Ch." + chapter.getChapterNumber() + " has been published according to schedule.",
                "CHAPTER",
                chapter.getId()
        );
        webSocketService.sendToUser(series.getMangaka().getId(), "CHAPTER_PUBLISHED", message);

        if (series.getTantouEditor() != null) {
            notificationService.createAndSend(
                    series.getTantouEditor().getId(),
                    "CHAPTER_PUBLISHED",
                    "Chapter published",
                    "Ch." + chapter.getChapterNumber() + " has been published on schedule.",
                    "CHAPTER",
                    chapter.getId()
            );
            webSocketService.sendToUser(series.getTantouEditor().getId(), "CHAPTER_PUBLISHED", message);
        }

        log.info("✅ Published Ch.{} of series '{}'", chapter.getChapterNumber(), series.getTitle());
    }

    /**
     * handleUnapprovedChapter: CASE 2 — Chapter tồn tại nhưng chưa APPROVED.
     * <p>
     * Hành động:
     * - Tăng missCount lên 1
     * - Gửi cảnh báo cho mangaka + tantou
     * - Chapter không được publish, sẽ thử lại vào kỳ sau
     */
    private void handleUnapprovedChapter(Chapter chapter, PublicationSchedule schedule,
                                         Series series, Integer nextChNumber) {
        schedule.setMissCount(schedule.getMissCount() + 1);
        scheduleRepository.save(schedule);

        log.warn("⚠️ Series '{}' Ch.{} exists but not APPROVED (status: {}). Miss count: {}",
                series.getTitle(), nextChNumber, chapter.getStatus(), schedule.getMissCount());

        // Cảnh báo mangaka
        notificationService.createAndSend(
                series.getMangaka().getId(),
                "CHAPTER_MISSED",
                "Chapter missed - not approved by tantou",
                "Ch." + nextChNumber + " is due for publishing but has not been approved"
                        + " by the tantou editor yet. Current status: " + chapter.getStatus(),
                "CHAPTER",
                chapter.getId()
        );

        // Cảnh báo tantou
        if (series.getTantouEditor() != null) {
            notificationService.createAndSend(
                    series.getTantouEditor().getId(),
                    "CHAPTER_MISSED",
                    "Chapter due - needs your approval",
                    "Ch." + nextChNumber + " of \"" + series.getTitle()
                            + "\" is due for publishing. Please review and approve.",
                    "CHAPTER",
                    chapter.getId()
            );
        }
    }

    /**
     * handleMissingChapter: CASE 3 — Chapter chưa được tạo.
     * <p>
     * Hành động:
     * - Tăng missCount lên 1
     * - Gửi cảnh báo cho mangaka (yêu cầu tạo chapter)
     * - Gửi cảnh báo cho chief (series bị chậm)
     */
    private void handleMissingChapter(PublicationSchedule schedule, Series series, Integer nextChNumber) {
        schedule.setMissCount(schedule.getMissCount() + 1);
        scheduleRepository.save(schedule);

        log.warn("⚠️ Series '{}' Ch.{} not found. Miss count: {}",
                series.getTitle(), nextChNumber, schedule.getMissCount());

        // Cảnh báo mangaka
        notificationService.createAndSend(
                series.getMangaka().getId(),
                "CHAPTER_MISSED",
                "Chapter missing - not created yet",
                "Ch." + nextChNumber + " is due for publishing but has not been created yet."
                        + " Please create and complete this chapter as soon as possible.",
                "SERIES",
                series.getId()
        );

        // Cảnh báo tất cả CHIEF_EDITOR
        notifyChiefs(
                "CHAPTER_MISSED_CHIEF",
                "Series delayed - chapter not created",
                "Series \"" + series.getTitle() + "\" is late on Ch." + nextChNumber
                        + " because the chapter has not been created yet."
                        + " (Miss count: " + schedule.getMissCount() + ")",
                series.getId()
        );
    }

    /**
     * checkMissThreshold: Kiểm tra nếu missCount >= 3 → đề xuất hủy + PAUSED.
     * <p>
     * Hành động:
     * - Gửi notification đến chief
     * - Tự động PAUSED schedule
     */
    private void checkMissThreshold(PublicationSchedule schedule, Series series) {
        if (schedule.getMissCount() >= 3) {
            // PAUSED schedule
            schedule.setStatus(ScheduleStatus.PAUSED);
            scheduleRepository.save(schedule);

            // Cảnh báo tất cả CHIEF_EDITOR
            notifyChiefs(
                    "SCHEDULE_PAUSED",
                    "Schedule paused - too many consecutive misses",
                    "Series \"" + series.getTitle() + "\" has missed "
                            + schedule.getMissCount() + " consecutive publications."
                            + " The schedule has been automatically paused."
                            + " Consider cancelling the series or putting it on hiatus.",
                    series.getId()
            );

            log.warn("🚫 Schedule for series '{}' PAUSED due to {} consecutive misses",
                    series.getTitle(), schedule.getMissCount());
        }
    }

    /**
     * notifyChiefs: Gửi notification đến tất cả user có role CHIEF_EDITOR.
     * Dùng để cảnh báo khi series bị chậm hoặc schedule bị PAUSED.
     *
     * @param type        Loại notification
     * @param title       Tiêu đề
     * @param message     Nội dung
     * @param referenceId ID series tham chiếu
     */
    private void notifyChiefs(String type, String title, String message, Long referenceId) {
        List<User> chiefs = userRepository.findByRole(Role.CHIEF_EDITOR);
        for (User chief : chiefs) {
            notificationService.createAndSend(
                    chief.getId(),
                    type,
                    title,
                    message,
                    "SERIES",
                    referenceId
            );
        }
    }
}
