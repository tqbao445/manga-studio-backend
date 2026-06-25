package com.mangaflow.studio.controller.dashboard;

import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.dashboard.response.ActivityFeedResponse;
import com.mangaflow.studio.service.dashboard.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ── DashboardController ──
 * Controller duy nhất cho Dashboard API.
 * <p>
 * ═══════════════════════════════════════════════════════════════
 *  Các endpoints:
 * ═══════════════════════════════════════════════════════════════
 *  1. GET /api/v1/dashboard/stats
 *     → Trả về thống kê dashboard khác nhau tuỳ role
 *     → Yêu cầu: user đã đăng nhập (bất kỳ role nào)
 *     → Response tuỳ role:
 *         - MANGAKA         → MangakaStatsResponse
 *         - ASSISTANT       → AssistantStatsResponse
 *         - TANTOU_EDITOR   → EditorStatsResponse
 *         - EDITORIAL_BOARD → BoardStatsResponse
 *         - CHIEF_EDITOR    → BoardStatsResponse
 * <p>
 *  2. GET /api/v1/dashboard/activity-feed
 *     → Trả về 20 hoạt động gần đây nhất
 *     → Yêu cầu: user đã đăng nhập (bất kỳ role nào)
 * <p>
 * ═══════════════════════════════════════════════════════════════
 *  CÁCH HOẠT ĐỘNG:
 * ═══════════════════════════════════════════════════════════════
 *  Khi frontend gọi API kèm JWT token:
 *  1. JwtAuthFilter kiểm tra token → giải mã → set SecurityContext
 *  2. Spring Security kiểm tra @PreAuthorize("isAuthenticated()")
 *  3. Controller nhận Authentication → lấy CustomUserDetails
 *  4. Gọi DashboardService.getStats(user) → nhận DTO
 *  5. Trả về ResponseEntity<DTO> → Jackson serialize → JSON
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "API dashboard tổng quan theo role")
public class DashboardController {

    /**
     * DashboardService — chứa toàn bộ business logic.
     * Spring tự động inject qua constructor nhờ @RequiredArgsConstructor.
     */
    private final DashboardService dashboardService;

    /**
     * ─── GET /api/v1/dashboard/stats ───
     * Trả về thống kê dashboard khác nhau tuỳ role của user đang login.
     * <p>
     * ═══════════════════════════════════════════════════════════════
     *  LUỒNG XỬ LÝ:
     * ═══════════════════════════════════════════════════════════════
     *  1. @PreAuthorize("isAuthenticated()"):
     *     → Spring Security kiểm tra user đã login chưa
     *     → Nếu chưa → trả về 401 Unauthorized
     *  2. Authentication authentication:
     *     → Spring tự động inject thông tin user từ SecurityContext
     *     → authentication.getPrincipal() = CustomUserDetails
     *  3. dashboardService.getStats(user):
     *     → Service xử lý business logic (đếm, query DB,...)
     *     → Trả về DTO khác nhau tuỳ role
     *  4. ResponseEntity.ok():
     *     → HTTP 200 + body là DTO
     *     → Jackson tự động serialize DTO thành JSON
     * <p>
     * 📌 ResponseEntity<?>: kiểu trả về không cố định
     *    vì mỗi role có response DTO khác nhau.
     *    Jackson vẫn serialize được nhờ tính đa hình.
     *
     * @param authentication Thông tin user đang login (từ JWT)
     * @return DTO thống kê tuỳ theo role của user
     */
    @Operation(summary = "Get dashboard stats",
            description = "Trả về thống kê dashboard khác nhau tuỳ role: "
                    + "MANGAKA, ASSISTANT, TANTOU_EDITOR, EDITORIAL_BOARD, CHIEF_EDITOR")
    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getStats(Authentication authentication) {
        // Lấy thông tin user từ Authentication
        // authentication.getPrincipal() trả về Object
        // Cast sang CustomUserDetails (class do project tự định nghĩa)
        CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();

        // Gọi Service → nhận DTO → trả về HTTP 200
        return ResponseEntity.ok(dashboardService.getStats(user));
    }

    /**
     * ─── GET /api/v1/dashboard/activity-feed ───
     * Trả về 20 hoạt động gần đây nhất trong hệ thống.
     * <p>
     * ═══════════════════════════════════════════════════════════════
     *  LUỒNG XỬ LÝ:
     * ═══════════════════════════════════════════════════════════════
     *  1. @PreAuthorize("isAuthenticated()"):
     *     → Bất kỳ user nào đã login cũng có thể xem activity feed
     *  2. dashboardService.getActivityFeed():
     *     → Lấy 20 notification gần đây nhất từ DB
     *     → Map Notification → ActivityFeedResponse
     *     → Kèm userName (tra cứu từ User table)
     *  3. ResponseEntity.ok():
     *     → HTTP 200 + body là List<ActivityFeedResponse>
     * <p>
     * 📌 Response là List<ActivityFeedResponse> — kiểu cố định,
     *    không phải <?> như getStats().
     *
     * @return Danh sách 20 hoạt động gần đây (dạng JSON array)
     */
    @Operation(summary = "Get activity feed",
            description = "Trả về 20 hoạt động gần đây nhất trong hệ thống")
    @GetMapping("/activity-feed")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ActivityFeedResponse>> getActivityFeed() {
        return ResponseEntity.ok(dashboardService.getActivityFeed());
    }
}
