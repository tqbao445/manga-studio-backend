package com.mangaflow.studio.controller.dashboard;

import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.dashboard.request.NudgeRequest;
import com.mangaflow.studio.dto.dashboard.response.EarningsResponse;
import com.mangaflow.studio.dto.dashboard.response.MangakaStatsResponse;
import com.mangaflow.studio.service.dashboard.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ── DashboardController ──
 * Controller cho dashboard module — tổng hợp số liệu cho 5 actor.
 * <p>
 * Base URL: /api/v1/dashboard
 * <p>
 * ══════════════════════════════════════════════════════════════════
 *  Các endpoint:
 * ══════════════════════════════════════════════════════════════════
 *  1. GET  /api/v1/dashboard/stats              → MANGAKA / TANTOU_EDITOR
 *  2. GET  /api/v1/dashboard/earnings           → ASSISTANT
 *  3. POST /api/v1/dashboard/nudge/{authorId}   → TANTOU_EDITOR
 * <p>
 * 📌 @RestController: Spring Bean — tự động serialize response JSON.
 * 📌 @RequestMapping("/api/v1/dashboard"): Base path.
 * 📌 @RequiredArgsConstructor: Lombok — DI constructor.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "API dashboard — tổng quan số liệu cho 5 actor")
public class DashboardController {

    /**
     * dashboardService: Service layer — chứa logic nghiệp vụ dashboard.
     */
    private final DashboardService dashboardService;

    // ════════════════════════════════════════════════════════════════
    // 1. GET /api/v1/dashboard/stats
    // ════════════════════════════════════════════════════════════════
    //
    // Mục đích:
    //   Trả về số liệu tổng quan dashboard. Tuỳ role mà response khác nhau:
    //   - MANGAKA:       activeSeries, ongoingChapters, pendingTasks,
    //                    submissionsToReview, upcomingDeadlines, rank
    //   - TANTOU_EDITOR: assignedSeries, pendingComments,
    //                    chaptersInReviewList, lateStudiosAlert
    //
    // Cách hoạt động:
    //   1. FE gọi GET /api/v1/dashboard/stats (kèm JWT)
    //   2. Controller lấy user từ @AuthenticationPrincipal
    //   3. Kiểm tra role — nếu MANGAKA hoặc TANTOU_EDITOR → tính toán
    //   4. Trả về MangakaStatsResponse (các field không dùng = null)
    //
    // ════════════════════════════════════════════════════════════════
    @Operation(summary = "Dashboard stats",
            description = "Số liệu tổng quan dashboard. "
                    + "MANGAKA: activeSeries, ongoingChapters, pendingTasks, "
                    + "submissionsToReview, upcomingDeadlines, rank. "
                    + "TANTOU_EDITOR: assignedSeries, pendingComments, "
                    + "chaptersInReviewList, lateStudiosAlert.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dashboard stats"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Role không được hỗ trợ")
    })
    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MangakaStatsResponse> getStats(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(dashboardService.getStats(user));
    }

    // ════════════════════════════════════════════════════════════════
    // 2. GET /api/v1/dashboard/earnings
    // ════════════════════════════════════════════════════════════════
    //
    // Mục đích:
    //   Trả về biểu đồ thu nhập của ASSISTANT.
    //
    // Cách hoạt động:
    //   1. FE gọi GET /api/v1/dashboard/earnings?groupBy=week|month
    //   2. Backend query task DONE của ASSISTANT hiện tại
    //   3. Tính amount dựa trên priority
    //   4. Group theo period (ISO week hoặc month)
    //   5. Trả về mảng EarningsResponse
    //
    // ════════════════════════════════════════════════════════════════
    @Operation(summary = "Earning Statement",
            description = "Biểu đồ thu nhập của ASSISTANT. "
                    + "Group theo week (ISO week) hoặc month. "
                    + "Amount tính dựa trên priority của task DONE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Danh sách thu nhập theo kỳ")
    })
    @GetMapping("/earnings")
    @PreAuthorize("hasRole('ASSISTANT')")
    public ResponseEntity<List<EarningsResponse>> getEarnings(
            @Parameter(description = "Group by: week hoặc month", example = "week")
            @RequestParam(defaultValue = "week") String groupBy,

            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(dashboardService.getEarnings(groupBy, user));
    }

    // ════════════════════════════════════════════════════════════════
    // 3. POST /api/v1/dashboard/nudge/{authorId}
    // ════════════════════════════════════════════════════════════════
    //
    // Mục đích:
    //   Tantou Editor gửi nhắc nhở (notification) cho Mangaka
    //   về chapter sắp trễ deadline.
    //
    // Cách hoạt động:
    //   1. Tantou click "Quick Nudge" trên Late Studios Alert
    //   2. FE gửi POST với body { chapterId, message }
    //   3. Backend kiểm tra quyền, tạo notification, push WebSocket
    //   4. Trả về 200 OK
    //
    // ════════════════════════════════════════════════════════════════
    @Operation(summary = "Quick Nudge",
            description = "Tantou Editor gửi nhắc nhở cho Mangaka. "
                    + "Body gồm chapterId (bắt buộc) và message (bắt buộc). "
                    + "Backend tạo Notification và push WebSocket realtime.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã gửi nudge thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
            @ApiResponse(responseCode = "403", description = "Không phải Tantou của series này"),
            @ApiResponse(responseCode = "404", description = "User hoặc Chapter không tồn tại")
    })
    @PostMapping("/nudge/{authorId}")
    @PreAuthorize("hasRole('TANTOU_EDITOR')")
    public ResponseEntity<Void> sendNudge(
            @Parameter(description = "ID của tác giả (MANGAKA) cần nhắc", example = "11")
            @PathVariable Long authorId,

            @Parameter(description = "Thông tin nudge: chapterId + message")
            @Valid @RequestBody NudgeRequest request,

            @AuthenticationPrincipal CustomUserDetails user) {

        dashboardService.sendNudge(authorId, request, user);
        return ResponseEntity.ok().build();
    }
}
