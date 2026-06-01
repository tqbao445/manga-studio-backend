package com.mangaflow.studio.controller.series;

import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.series.request.InviteAssistantRequest;
import com.mangaflow.studio.dto.series.response.SeriesAssistantResponse;
import com.mangaflow.studio.service.series.SeriesAssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ── SeriesAssistantController ──
 * Controller quản lý assistant trong series (dành cho MANGAKA).
 * <p>
 * 📌 Base URL: /api/series/{seriesId}/assistants
 * <p>
 * ═══════════════════════════════════════════════════════
 *  3 endpoints:
 * ═══════════════════════════════════════════════════════
 *  POST   /api/series/{seriesId}/assistants/invite   — Mời assistant
 *  GET    /api/series/{seriesId}/assistants           — Danh sách ACCEPTED
 *  DELETE /api/series/{seriesId}/assistants/{id}      — Xoá assistant
 * ═══════════════════════════════════════════════════════
 */
@RestController
@RequestMapping("/api/series/{seriesId}/assistants")
@RequiredArgsConstructor
@Tag(name = "Series - Assistants", description = "Quản lý ASSISTANT trong series — MANGAKA invite/remove, mọi role xem danh sách")
public class SeriesAssistantController {

    private final SeriesAssistantService seriesAssistantService;

    // ════════════════════════════════════════════════════════════════
    // 1. POST /api/series/{seriesId}/assistants/invite
    // ════════════════════════════════════════════════════════════════

    /**
     * MANGAKA gửi lời mời cho 1 ASSISTANT tham gia series.
     * <p>
     * 📌 @PreAuthorize("hasRole('MANGAKA')"):
     *    Chỉ MANGAKA mới được gọi endpoint này.
     *    Nếu ASSISTANT hoặc role khác gọi → Spring trả về 403.
     * <p>
     * 📌 Luồng xử lý (SeriesAssistantService.inviteAssistant()):
     *    1. Kiểm tra series tồn tại + currentUser là mangaka
     *    2. Kiểm tra assistant tồn tại + có role ASSISTANT
     *    3. Kiểm tra chưa có lời mời PENDING hoặc ACCEPTED
     *    4. Tạo record mới hoặc cập nhật REJECTED → PENDING
     * <p>
     * 📌 Request body:
     *    { "assistantId": 5 }
     *
     * @param seriesId    ID của series (từ path variable)
     * @param request     DTO chứa assistantId
     * @param currentUser User đang đăng nhập (MANGAKA)
     * @return 201 CREATED + SeriesAssistantResponse — lời mời vừa tạo
     */
    @Operation(
            summary = "Mời ASSISTANT vào series",
            description = "MANGAKA gửi lời mời cho 1 user có role ASSISTANT tham gia series. " +
                    "Nếu assistant đã bị REJECTED trước đó → cập nhật lại thành PENDING. " +
                    "Không thể mời nếu đã có lời mời PENDING hoặc ACCEPTED."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lời mời đã được tạo",
                    content = @Content(schema = @Schema(implementation = SeriesAssistantResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ — assistantId null / đã có lời mời / không phải ASSISTANT"),
            @ApiResponse(responseCode = "403", description = "Không có quyền — chỉ MANGAKA mới được mời"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy series hoặc user")
    })
    @PostMapping("/invite")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<SeriesAssistantResponse> inviteAssistant(
            @Parameter(description = "ID của series", example = "1")
            @PathVariable Long seriesId,
            @Valid @RequestBody InviteAssistantRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        SeriesAssistantResponse response = seriesAssistantService
                .inviteAssistant(seriesId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ════════════════════════════════════════════════════════════════
    // 2. GET /api/series/{seriesId}/assistants
    // ════════════════════════════════════════════════════════════════

    /**
     * Lấy danh sách assistant đã ACCEPTED của 1 series.
     * <p>
     * 📌 Ai cũng có thể xem (không cần @PreAuthorize):
     *    - MANGAKA: xem team của mình
     *    - ASSISTANT: xem đồng nghiệp trong series
     *    - TANTOU_EDITOR / EDITORIAL_BOARD: xem ai đang làm series này
     * <p>
     * 📌 Chỉ trả về ACCEPTED — PENDING và REJECTED không hiển thị.
     *
     * @param seriesId ID của series
     * @return 200 OK + List<SeriesAssistantResponse> danh sách ACCEPTED
     */
    @Operation(
            summary = "Danh sách ASSISTANT trong series",
            description = "Trả về tất cả assistant đã ACCEPTED (đồng ý lời mời) trong series. " +
                    "PENDING và REJECTED không được trả về ở endpoint này. " +
                    "Tất cả authenticated user đều xem được."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Danh sách assistant đã ACCEPTED",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = SeriesAssistantResponse.class)))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy series")
    })
    @GetMapping
    public ResponseEntity<List<SeriesAssistantResponse>> getSeriesAssistants(
            @Parameter(description = "ID của series", example = "1")
            @PathVariable Long seriesId) {
        return ResponseEntity.ok(
                seriesAssistantService.getSeriesAssistants(seriesId));
    }

    // ════════════════════════════════════════════════════════════════
    // 3. DELETE /api/series/{seriesId}/assistants/{assistantId}
    // ════════════════════════════════════════════════════════════════

    /**
     * MANGAKA xoá assistant khỏi series.
     * <p>
     * 📌 @PreAuthorize("hasRole('MANGAKA')"):
     *    Chỉ MANGAKA mới được xoá thành viên khỏi series.
     * <p>
     * 📌 Hành vi:
     *    - Xoá record trong bảng series_assistant (bất kể trạng thái nào)
     *    - Task của assistant đó trong series vẫn tồn tại
     *      (cần reassign nếu muốn)
     * <p>
     * 📌 Luồng xử lý (SeriesAssistantService.removeAssistant()):
     *    1. Kiểm tra series tồn tại + currentUser là mangaka
     *    2. Kiểm tra record series_assistant tồn tại
     *    3. Xoá record
     *
     * @param seriesId    ID của series
     * @param assistantId ID của assistant cần xoá
     * @param currentUser User đang đăng nhập (MANGAKA)
     * @return 204 NO CONTENT — xoá thành công
     */
    @Operation(
            summary = "Xoá ASSISTANT khỏi series",
            description = "MANGAKA xoá 1 assistant khỏi series. " +
                    "Record trong bảng series_assistant bị xoá hoàn toàn (bất kể trạng thái nào). " +
                    "Task của assistant đó trong series vẫn tồn tại (cần reassign nếu muốn)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Xoá thành công — không có nội dung trả về"),
            @ApiResponse(responseCode = "403", description = "Không có quyền — chỉ MANGAKA"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy series hoặc record assistant")
    })
    @DeleteMapping("/{assistantId}")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<Void> removeAssistant(
            @Parameter(description = "ID của series", example = "1")
            @PathVariable Long seriesId,
            @Parameter(description = "ID của assistant cần xoá", example = "5")
            @PathVariable Long assistantId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        seriesAssistantService.removeAssistant(seriesId, assistantId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
