package com.mangaflow.studio.service.schedule;

import com.mangaflow.studio.common.exception.AppException;
import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.schedule.mapper.ScheduleMapper;
import com.mangaflow.studio.dto.schedule.request.CreateScheduleRequest;
import com.mangaflow.studio.dto.schedule.response.ScheduleResponse;
import com.mangaflow.studio.model.schedule.PublicationSchedule;
import com.mangaflow.studio.model.schedule.ScheduleStatus;
import com.mangaflow.studio.model.schedule.ScheduleType;
import com.mangaflow.studio.model.series.Series;
import com.mangaflow.studio.model.series.SeriesStatus;
import com.mangaflow.studio.repository.schedule.PublicationScheduleRepository;
import com.mangaflow.studio.repository.series.SeriesRepository;
import com.mangaflow.studio.service.common.WebSocketService;
import com.mangaflow.studio.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ── PublicationScheduleService ──
 * Service xử lý toàn bộ logic nghiệp vụ liên quan đến lịch phát hành series.
 *
 * ═══════════════════════════════════════════════════
 *  Các chức năng:
 *    - Tạo / Sửa / Xoá / Xem lịch phát hành
 *    - Chuyển trạng thái lịch (PAUSE / RESUME / COMPLETE)
 *    - Reset missCount (khi EB cho mangaka cơ hội)
 *    - Auto PAUSE khi series rời khỏi ONGOING (gọi từ SeriesWorkflowService)
 *    - Lấy danh sách ACTIVE cho cron job (AutoPublishService)
 * ═══════════════════════════════════════════════════
 */
@Service
@RequiredArgsConstructor
public class PublicationScheduleService {

    private final PublicationScheduleRepository scheduleRepository;
    private final SeriesRepository seriesRepository;
    private final ScheduleMapper scheduleMapper;
    private final WebSocketService webSocketService;
    private final NotificationService notificationService;

    // ═════════════════════════════════════════════════════════
    //  GET ALL — Danh sách schedules (phân trang + filter)
    // ═════════════════════════════════════════════════════════

    /**
     * Lấy danh sách schedules với phân trang và lọc theo trạng thái.
     *
     * 📌 Dùng JPA Specification để build WHERE clause động:
     *    - Nếu status != null → WHERE status = :status
     *    - Nếu search != null → WHERE series.title LIKE %:search%
     *
     * 📌 Phân trang dùng Pageable từ Spring:
     *    page, size, sort do controller truyền vào.
     *
     * @param status   Lọc theo trạng thái (ACTIVE / PAUSED / COMPLETED) — không bắt buộc
     * @param search   Tìm kiếm theo tên series — không bắt buộc
     * @param pageable Thông tin phân trang (page, size, sort)
     * @param user     User đang đăng nhập
     * @return Page<ScheduleResponse> danh sách schedules đã phân trang
     */
    @Transactional(readOnly = true)
    public Page<ScheduleResponse> getAllSchedules(ScheduleStatus status, String search, Pageable pageable,
                                                  CustomUserDetails user) {
        // ── Build Specification động ──
        Specification<PublicationSchedule> spec = Specification.where(null);

        // Nếu có lọc theo status: WHERE status = :status
        if (status != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("status"), status));
        }

        // Nếu có tìm kiếm theo tên series: WHERE series.title LIKE %:search%
        if (search != null && !search.trim().isEmpty()) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("series").get("title")),
                            "%" + search.toLowerCase() + "%"));
        }

        // Gọi repository với Specification + Pageable
        return scheduleRepository.findAll(spec, pageable)
                .map(scheduleMapper::toResponse);
    }

    // ═════════════════════════════════════════════════════════
    //  CREATE — Tạo lịch phát hành mới
    // ═════════════════════════════════════════════════════════

    /**
     * Tạo lịch phát hành mới cho series.
     *
     * 📌 Luồng xử lý:
     *    1. Kiểm tra series tồn tại
     *    2. Kiểm tra series đang ONGOING
     *    3. Kiểm tra chưa có schedule ACTIVE nào
     *    4. Validate request: WEEKLY → cần dayOfWeek, MONTHLY → cần dayOfMonth
     *    5. Map request → entity (nextChapterNumber=1, missCount=0, status=ACTIVE)
     *    6. Save xuống DB
     *    7. Gửi notification + websocket cho mangaka
     *
     * @param seriesId ID của series
     * @param request  DTO chứa scheduleType, dayOfWeek/dayOfMonth, startDate
     * @param user     User đang đăng nhập (EB/Chief)
     * @return ScheduleResponse — schedule vừa tạo
     */
    @Transactional
    public ScheduleResponse createSchedule(Long seriesId, CreateScheduleRequest request, CustomUserDetails user) {
        // Bước 1: Kiểm tra series tồn tại
        Series series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Series not found"));

        // Bước 2: Kiểm tra series đang ONGOING
        if (series.getStatus() != SeriesStatus.ONGOING) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Only ONGOING series can have a publication schedule");
        }

        // Bước 3: Kiểm tra chưa có schedule ACTIVE
        if (scheduleRepository.findBySeriesIdAndStatus(seriesId, ScheduleStatus.ACTIVE).isPresent()) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "This series already has an active schedule");
        }

        // Bước 4: Validate request
        validateScheduleRequest(request);

        // Bước 5 + 6: Map và save
        PublicationSchedule schedule = scheduleMapper.toEntity(request, series);
        schedule = scheduleRepository.save(schedule);

        // Bước 7: Gửi thông báo cho mangaka
        notificationService.createAndSend(
                series.getMangaka().getId(),
                "SCHEDULE_CREATED",
                "Publication schedule created",
                "A publication schedule has been created for \"" + series.getTitle()
                        + "\" starting " + request.getStartDate() + ".",
                "SERIES",
                series.getId()
        );

        webSocketService.sendToUser(series.getMangaka().getId(), "SCHEDULE_CREATED",
                "A publication schedule has been created for \"" + series.getTitle() + "\"");

        return scheduleMapper.toResponse(schedule);
    }

    // ═════════════════════════════════════════════════════════
    //  GET BY SERIES — Lấy schedule ACTIVE của series
    // ═════════════════════════════════════════════════════════

    /**
     * Lấy schedule ACTIVE của series.
     * Dùng cho frontend hiển thị thông tin schedule trên Series Detail.
     *
     * @param seriesId ID của series
     * @return ScheduleResponse
     */
    @Transactional(readOnly = true)
    public ScheduleResponse getScheduleBySeries(Long seriesId) {
        seriesRepository.findById(seriesId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Series not found"));

        PublicationSchedule schedule = scheduleRepository
                .findBySeriesIdAndStatus(seriesId, ScheduleStatus.ACTIVE)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "No active schedule found for this series"));

        return scheduleMapper.toResponse(schedule);
    }

    // ═════════════════════════════════════════════════════════
    //  GET BY ID — Lấy chi tiết schedule
    // ═════════════════════════════════════════════════════════

    /**
     * Lấy chi tiết schedule theo ID.
     *
     * @param id ID của schedule
     * @return ScheduleResponse
     */
    @Transactional(readOnly = true)
    public ScheduleResponse getScheduleById(Long id) {
        PublicationSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Schedule not found"));
        return scheduleMapper.toResponse(schedule);
    }

    // ═════════════════════════════════════════════════════════
    //  UPDATE — Cập nhật cấu hình schedule
    // ═════════════════════════════════════════════════════════

    /**
     * Cập nhật cấu hình schedule (đổi WEEKLY↔MONTHLY, dayOfWeek, dayOfMonth, startDate).
     *
     * 📌 Khi update:
     *    - nextChapterNumber giữ nguyên (tiếp tục từ chapter hiện tại)
     *    - missCount = 0 (reset)
     *    - Các field khác update null-safe
     *
     * @param id      ID của schedule
     * @param request DTO chứa dữ liệu mới
     * @param user    User đang đăng nhập
     * @return ScheduleResponse — schedule đã cập nhật
     */
    @Transactional
    public ScheduleResponse updateSchedule(Long id, CreateScheduleRequest request, CustomUserDetails user) {
        PublicationSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Schedule not found"));

        validateScheduleRequest(request);

        scheduleMapper.updateEntity(schedule, request);
        schedule.setMissCount(0); // Reset missCount khi đổi lịch

        schedule = scheduleRepository.save(schedule);
        return scheduleMapper.toResponse(schedule);
    }

    // ═════════════════════════════════════════════════════════
    //  UPDATE STATUS — Đổi trạng thái schedule
    // ═════════════════════════════════════════════════════════

    /**
     * Đổi trạng thái schedule (PAUSE / RESUME / COMPLETE).
     *
     * 📌 Luồng:
     *    - PAUSE: cron job bỏ qua, giữ nguyên nextChapterNumber + missCount
     *    - RESUME: cron job tiếp tục xử lý
     *    - COMPLETE: kết thúc, không thể RESUME
     *
     * @param id     ID của schedule
     * @param status Trạng thái mới
     * @param user   User đang đăng nhập
     * @return ScheduleResponse
     */
    @Transactional
    public ScheduleResponse updateScheduleStatus(Long id, ScheduleStatus status, CustomUserDetails user) {
        PublicationSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Schedule not found"));

        if (schedule.getStatus() == ScheduleStatus.COMPLETED) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Cannot change status of a completed schedule");
        }

        schedule.setStatus(status);
        schedule = scheduleRepository.save(schedule);
        return scheduleMapper.toResponse(schedule);
    }

    // ═════════════════════════════════════════════════════════
    //  RESET MISS — Reset missCount về 0
    // ═════════════════════════════════════════════════════════

    /**
     * Reset missCount về 0.
     * Dùng khi EB muốn cho mangaka cơ hội sau khi đã bị trễ.
     *
     * @param id   ID của schedule
     * @param user User đang đăng nhập
     * @return ScheduleResponse
     */
    @Transactional
    public ScheduleResponse resetMissCount(Long id, CustomUserDetails user) {
        PublicationSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Schedule not found"));

        schedule.setMissCount(0);
        schedule = scheduleRepository.save(schedule);
        return scheduleMapper.toResponse(schedule);
    }

    // ═════════════════════════════════════════════════════════
    //  DELETE — Xoá schedule
    // ═════════════════════════════════════════════════════════

    /**
     * Xoá schedule khỏi hệ thống.
     *
     * @param id   ID của schedule
     * @param user User đang đăng nhập
     */
    @Transactional
    public void deleteSchedule(Long id, CustomUserDetails user) {
        PublicationSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Schedule not found"));

        scheduleRepository.delete(schedule);
    }

    // ═════════════════════════════════════════════════════════
    //  HELPER: PAUSE BY SERIES ID
    // ═════════════════════════════════════════════════════════

    /**
     * Tự động PAUSE schedule khi series rời khỏi ONGOING.
     * Được gọi từ SeriesWorkflowService khi đổi status series.
     *
     * @param seriesId ID của series
     */
    @Transactional
    public void pauseBySeriesId(Long seriesId) {
        scheduleRepository.findBySeriesIdAndStatus(seriesId, ScheduleStatus.ACTIVE)
                .ifPresent(schedule -> {
                    schedule.setStatus(ScheduleStatus.PAUSED);
                    scheduleRepository.save(schedule);
                });
    }

    // ═════════════════════════════════════════════════════════
    //  HELPER: FIND ACTIVE SCHEDULES (cho cron job)
    // ═════════════════════════════════════════════════════════

    /**
     * Lấy tất cả schedules đang ACTIVE.
     * Dùng cho AutoPublishService (cron job) quét và xử lý.
     *
     * @return List<PublicationSchedule> danh sách schedule ACTIVE
     */
    @Transactional(readOnly = true)
    public List<PublicationSchedule> findActiveSchedules() {
        return scheduleRepository.findByStatus(ScheduleStatus.ACTIVE);
    }

    // ═════════════════════════════════════════════════════════
    //  VALIDATION
    // ═════════════════════════════════════════════════════════

    /**
     * Validate request dựa trên scheduleType:
     *   - WEEKLY:  dayOfWeek bắt buộc (1..7)
     *   - MONTHLY: dayOfMonth bắt buộc (1..31)
     *
     * @param request DTO cần validate
     */
    private void validateScheduleRequest(CreateScheduleRequest request) {
        if (request.getScheduleType() == ScheduleType.WEEKLY && request.getDayOfWeek() == null) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "dayOfWeek is required for WEEKLY schedule");
        }
        if (request.getScheduleType() == ScheduleType.MONTHLY && request.getDayOfMonth() == null) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "dayOfMonth is required for MONTHLY schedule");
        }
        if (request.getScheduleType() == ScheduleType.WEEKLY
                && (request.getDayOfWeek() < 1 || request.getDayOfWeek() > 7)) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "dayOfWeek must be between 1 (Monday) and 7 (Sunday)");
        }
        if (request.getScheduleType() == ScheduleType.MONTHLY
                && (request.getDayOfMonth() < 1 || request.getDayOfMonth() > 31)) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "dayOfMonth must be between 1 and 31");
        }
    }
}
