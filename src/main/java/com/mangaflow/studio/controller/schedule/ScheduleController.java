package com.mangaflow.studio.controller.schedule;

import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.schedule.request.CreateScheduleRequest;
import com.mangaflow.studio.dto.schedule.response.ScheduleResponse;
import com.mangaflow.studio.model.schedule.ScheduleStatus;
import com.mangaflow.studio.service.schedule.PublicationScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ── ScheduleController ──
 * Controller xử lý tất cả API liên quan đến lịch phát hành series (PublicationSchedule).
 *
 * ═══════════════════════════════════════════════════
 *  Base path: /api
 *  Quyền:     Tuỳ endpoint (isAuthenticated / EDITORIAL_BOARD + CHIEF_EDITOR)
 *
 *  DANH SÁCH API:
 *  ┌──────────┬──────────────────────────────────────────┬──────────────────────────────┐
 *  │ Method   │ Endpoint                                 │ Chức năng                    │
 *  ├──────────┼──────────────────────────────────────────┼──────────────────────────────┤
 *  │ GET      │ /api/schedules                           │ Danh sách schedules (paging) │
 *  │ POST     │ /api/series/{seriesId}/schedule          │ Tạo lịch phát hành           │
 *  │ GET      │ /api/series/{seriesId}/schedule          │ Xem schedule của series      │
 *  │ GET      │ /api/schedules/{id}                      │ Xem chi tiết schedule        │
 *  │ PUT      │ /api/schedules/{id}                      │ Sửa cấu hình schedule        │
 *  │ PATCH    │ /api/schedules/{id}/status               │ Đổi trạng thái               │
 *  │ PATCH    │ /api/schedules/{id}/reset-miss           │ Reset missCount              │
 *  │ DELETE   │ /api/schedules/{id}                      │ Xoá schedule                 │
 *  └──────────┴──────────────────────────────────────────┴──────────────────────────────┘
 * ═══════════════════════════════════════════════════
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Publication Schedule", description = "Quản lý lịch phát hành định kỳ cho series — WEEKLY / MONTHLY")
public class ScheduleController {

    private final PublicationScheduleService scheduleService;

    // ═════════════════════════════════════════════════════════
    //  1. GET ALL SCHEDULES — Danh sách schedules (phân trang)
    // ═════════════════════════════════════════════════════════

    /**
     * GET /api/schedules
     *
     * Lấy danh sách schedules với filter theo status + tìm kiếm + phân trang.
     * Dùng cho trang Schedule ở frontend (Publication Schedules).
     *
     * 📌 Query Parameters:
     *    - status: Lọc theo trạng thái (ACTIVE / PAUSED / COMPLETED) — không bắt buộc
     *    - search: Tìm kiếm theo tên series (LIKE %search%) — không bắt buộc
     *    - page:   Số trang (mặc định 0)
     *    - size:   Số bản ghi mỗi trang (mặc định 20, tối đa 100)
     *    - sort:   Sắp xếp theo field (mặc định "createdAt,desc")
     *
     * @param status Lọc theo trạng thái
     * @param search Tìm kiếm theo tên series
     * @param page   Số trang (0-indexed)
     * @param size   Số bản ghi mỗi trang
     * @param sort   Field sắp xếp (VD: "createdAt,desc")
     * @param user   Thông tin user từ JWT
     * @return 200 OK + Page<ScheduleResponse>
     */
    @Operation(summary = "Danh sách lịch phát hành",
               description = "Lấy danh sách schedules với filter theo status, tìm kiếm và phân trang. "
                       + "Tất cả user đã đăng nhập đều xem được.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Danh sách schedules (phân trang)"),
        @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @GetMapping("/schedules")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<ScheduleResponse>> getAllSchedules(
            @Parameter(description = "Lọc theo trạng thái: ACTIVE, PAUSED, COMPLETED")
            @RequestParam(required = false) ScheduleStatus status,

            @Parameter(description = "Tìm kiếm theo tên series (LIKE %search%)")
            @RequestParam(required = false) String search,

            @Parameter(description = "Số trang (bắt đầu từ 0)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Số lượng mỗi trang (tối đa 100)")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Sắp xếp (VD: createdAt,desc)")
            @RequestParam(defaultValue = "createdAt") String sortBy,

            @Parameter(description = "Hướng sắp xếp: asc hoặc desc")
            @RequestParam(defaultValue = "desc") String sortDir,

            @AuthenticationPrincipal CustomUserDetails user) {

        if (size > 100) size = 100;

        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);

        return ResponseEntity.ok(
                scheduleService.getAllSchedules(status, search, pageable, user));
    }

    // ═════════════════════════════════════════════════════════
    //  2. CREATE SCHEDULE — Tạo lịch phát hành mới (theo series)
    // ═════════════════════════════════════════════════════════

    /**
     * POST /api/series/{seriesId}/schedule
     *
     * Tạo lịch phát hành mới cho series.
     * Chỉ EDITORIAL_BOARD và CHIEF_EDITOR mới có quyền.
     *
     * @param seriesId ID của series cần tạo lịch
     * @param request  Body: scheduleType, dayOfWeek/dayOfMonth, startDate
     * @param user     Thông tin user từ JWT
     * @return 201 CREATED + ScheduleResponse
     */
    @Operation(summary = "Tạo lịch phát hành cho series",
               description = "Tạo lịch WEEKLY hoặc MONTHLY. Chỉ EDITORIAL_BOARD/CHIEF_EDITOR được dùng.")
    @PostMapping("/series/{seriesId}/schedule")
    @PreAuthorize("hasAnyRole('EDITORIAL_BOARD', 'CHIEF_EDITOR')")
    public ResponseEntity<ScheduleResponse> createSchedule(
            @PathVariable Long seriesId,
            @Valid @RequestBody CreateScheduleRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        ScheduleResponse response = scheduleService.createSchedule(seriesId, request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ═════════════════════════════════════════════════════════
    //  3. GET SCHEDULE BY SERIES — Xem schedule của series
    // ═════════════════════════════════════════════════════════

    /**
     * GET /api/series/{seriesId}/schedule
     *
     * Lấy thông tin schedule ACTIVE của series.
     * Tất cả user đã đăng nhập đều xem được.
     *
     * @param seriesId ID của series
     * @return 200 OK + ScheduleResponse
     */
    @Operation(summary = "Xem lịch phát hành của series",
               description = "Trả về thông tin schedule ACTIVE của series. Tất cả user đã đăng nhập đều xem được.")
    @GetMapping("/series/{seriesId}/schedule")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ScheduleResponse> getScheduleBySeries(
            @PathVariable Long seriesId) {
        return ResponseEntity.ok(scheduleService.getScheduleBySeries(seriesId));
    }

    // ═════════════════════════════════════════════════════════
    //  4. GET SCHEDULE BY ID — Xem chi tiết schedule
    // ═════════════════════════════════════════════════════════

    /**
     * GET /api/schedules/{id}
     *
     * Lấy chi tiết schedule theo ID.
     *
     * @param id ID của schedule
     * @return 200 OK + ScheduleResponse
     */
    @Operation(summary = "Xem chi tiết lịch phát hành")
    @GetMapping("/schedules/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ScheduleResponse> getScheduleById(
            @PathVariable Long id) {
        return ResponseEntity.ok(scheduleService.getScheduleById(id));
    }

    // ═════════════════════════════════════════════════════════
    //  5. UPDATE SCHEDULE — Sửa cấu hình schedule
    // ═════════════════════════════════════════════════════════

    /**
     * PUT /api/schedules/{id}
     *
     * Sửa cấu hình schedule (đổi WEEKLY↔MONTHLY, dayOfWeek, dayOfMonth, startDate).
     * Khi sửa: nextChapterNumber giữ nguyên, missCount reset về 0.
     * Chỉ EDITORIAL_BOARD và CHIEF_EDITOR mới có quyền.
     *
     * @param id      ID của schedule
     * @param request Body chứa dữ liệu mới
     * @param user    Thông tin user từ JWT
     * @return 200 OK + ScheduleResponse
     */
    @Operation(summary = "Sửa cấu hình lịch phát hành",
               description = "Đổi WEEKLY↔MONTHLY, sửa dayOfWeek/dayOfMonth, startDate. Reset missCount về 0.")
    @PutMapping("/schedules/{id}")
    @PreAuthorize("hasAnyRole('EDITORIAL_BOARD', 'CHIEF_EDITOR')")
    public ResponseEntity<ScheduleResponse> updateSchedule(
            @PathVariable Long id,
            @Valid @RequestBody CreateScheduleRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(scheduleService.updateSchedule(id, request, user));
    }

    // ═════════════════════════════════════════════════════════
    //  6. UPDATE STATUS — Đổi trạng thái schedule
    // ═════════════════════════════════════════════════════════

    /**
     * PATCH /api/schedules/{id}/status
     *
     * Đổi trạng thái schedule: PAUSE / RESUME / COMPLETE.
     * Chỉ EDITORIAL_BOARD và CHIEF_EDITOR mới có quyền.
     *
     * Body: { "status": "PAUSED" | "ACTIVE" | "COMPLETED" }
     *
     * @param id     ID của schedule
     * @param body   Map chứa key "status"
     * @param user   Thông tin user từ JWT
     * @return 200 OK + ScheduleResponse
     */
    @Operation(summary = "Đổi trạng thái lịch phát hành",
               description = "PAUSE (tạm dừng) / RESUME (tiếp tục) / COMPLETE (kết thúc). "
                       + "Không thể thay đổi status của schedule đã COMPLETED.")
    @PatchMapping("/schedules/{id}/status")
    @PreAuthorize("hasAnyRole('EDITORIAL_BOARD', 'CHIEF_EDITOR')")
    public ResponseEntity<ScheduleResponse> updateScheduleStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails user) {
        ScheduleStatus status = ScheduleStatus.valueOf(body.get("status"));
        return ResponseEntity.ok(scheduleService.updateScheduleStatus(id, status, user));
    }

    // ═════════════════════════════════════════════════════════
    //  7. RESET MISS COUNT — Reset missCount về 0
    // ═════════════════════════════════════════════════════════

    /**
     * PATCH /api/schedules/{id}/reset-miss
     *
     * Reset missCount về 0.
     * Dùng khi EB muốn cho mangaka cơ hội sau khi đã bị trễ.
     * Chỉ EDITORIAL_BOARD và CHIEF_EDITOR mới có quyền.
     *
     * @param id   ID của schedule
     * @param user Thông tin user từ JWT
     * @return 200 OK + ScheduleResponse
     */
    @Operation(summary = "Reset missCount",
               description = "Reset số lần trễ liên tiếp về 0. Dùng khi EB cho mangaka cơ hội.")
    @PatchMapping("/schedules/{id}/reset-miss")
    @PreAuthorize("hasAnyRole('EDITORIAL_BOARD', 'CHIEF_EDITOR')")
    public ResponseEntity<ScheduleResponse> resetMissCount(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(scheduleService.resetMissCount(id, user));
    }

    // ═════════════════════════════════════════════════════════
    //  8. DELETE SCHEDULE — Xoá schedule
    // ═════════════════════════════════════════════════════════

    /**
     * DELETE /api/schedules/{id}
     *
     * Xoá schedule khỏi hệ thống.
     * Chỉ EDITORIAL_BOARD và CHIEF_EDITOR mới có quyền.
     *
     * @param id   ID của schedule
     * @param user Thông tin user từ JWT
     * @return 204 NO CONTENT
     */
    @Operation(summary = "Xoá lịch phát hành")
    @DeleteMapping("/schedules/{id}")
    @PreAuthorize("hasAnyRole('EDITORIAL_BOARD', 'CHIEF_EDITOR')")
    public ResponseEntity<Void> deleteSchedule(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        scheduleService.deleteSchedule(id, user);
        return ResponseEntity.noContent().build();
    }
}
