package com.mangaflow.studio.controller.series;

import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.series.request.RespondInvitationRequest;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ── AssistantInvitationController ──
 * Controller quản lý lời mời tham gia series (dành cho ASSISTANT).
 * <p>
 * 📌 Base URL: /api/assistants/invitations
 * <p>
 * ═══════════════════════════════════════════════════════
 *  2 endpoints:
 * ═══════════════════════════════════════════════════════
 *  GET   /api/assistants/invitations        — Xem lời mời đang chờ
 *  PATCH /api/assistants/invitations/{id}   — Chấp nhận / từ chối
 * ═══════════════════════════════════════════════════════
 */
@RestController
@RequestMapping("/api/assistants/invitations")
@RequiredArgsConstructor
@Tag(name = "Assistant - Invitations", description = "ASSISTANT xem và phản hồi lời mời tham gia series")
public class AssistantInvitationController {

    private final SeriesAssistantService seriesAssistantService;

    // ════════════════════════════════════════════════════════════════
    // 1. GET /api/assistants/invitations
    // ════════════════════════════════════════════════════════════════

    /**
     * ASSISTANT xem danh sách lời mời PENDING của mình.
     * <p>
     * 📌 @PreAuthorize("hasRole('ASSISTANT')"):
     *    Chỉ ASSISTANT mới xem được lời mời của mình.
     *    MANGAKA / TANTOU_EDITOR / EDITORIAL_BOARD không có endpoint này.
     * <p>
     * 📌 Dùng trong:
     *    - InvitationsPage (frontend): hiển thị danh sách lời mời
     *    - Notification: badge đếm số lời mời chưa đọc
     * <p>
     * 📌 Chỉ trả về PENDING:
     *    ACCEPTED và REJECTED không hiển thị (đã xử lý xong).
     *    Nếu muốn xem lịch sử, cần endpoint riêng.
     *
     * @param currentUser User đang đăng nhập (ASSISTANT)
     * @return 200 OK + List<SeriesAssistantResponse> danh sách PENDING
     */
    @Operation(
            summary = "Xem lời mời đang chờ",
            description = "ASSISTANT xem danh sách lời mời PENDING (chưa phản hồi) từ các MANGAKA. " +
                    "Kết quả bao gồm thông tin series và người mời để hiển thị trên InvitationsPage."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Danh sách lời mời PENDING",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = SeriesAssistantResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Không có quyền — chỉ ASSISTANT")
    })
    @GetMapping
    @PreAuthorize("hasRole('ASSISTANT')")
    public ResponseEntity<List<SeriesAssistantResponse>> getPendingInvitations(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(
                seriesAssistantService.getPendingInvitations(currentUser));
    }

    // ════════════════════════════════════════════════════════════════
    // 2. PATCH /api/assistants/invitations/{id}
    // ════════════════════════════════════════════════════════════════

    /**
     * ASSISTANT phản hồi lời mời: chấp nhận hoặc từ chối.
     * <p>
     * 📌 @PreAuthorize("hasRole('ASSISTANT')"):
     *    Chỉ ASSISTANT mới phản hồi được lời mời.
     * <p>
     * 📌 Luồng xử lý (SeriesAssistantService.respondToInvitation()):
     *    1. Kiểm tra invitation tồn tại
     *    2. Kiểm tra invitation thuộc về currentUser
     *    3. Kiểm tra invitation đang PENDING
     *    4. Kiểm tra request.status là ACCEPTED hoặc REJECTED
     *    5. Cập nhật status + respondedAt
     * <p>
     * 📌 Request body:
     *    { "status": "ACCEPTED" }   — đồng ý
     *    { "status": "REJECTED" }   — từ chối
     *
     * @param invitationId ID của lời mời (từ path variable)
     * @param request      DTO chứa status (ACCEPTED / REJECTED)
     * @param currentUser  User đang đăng nhập (ASSISTANT)
     * @return 200 OK + SeriesAssistantResponse — lời mời sau khi phản hồi
     */
    @Operation(
            summary = "Phản hồi lời mời",
            description = "ASSISTANT chấp nhận hoặc từ chối lời mời tham gia series. " +
                    "Request body: {\"status\": \"ACCEPTED\"} hoặc {\"status\": \"REJECTED\"}. " +
                    "Chỉ phản hồi được lời mời đang PENDING và thuộc về chính mình."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Phản hồi thành công",
                    content = @Content(schema = @Schema(implementation = SeriesAssistantResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ — status null / không phải ACCEPTED/REJECTED / lời mời không ở PENDING"),
            @ApiResponse(responseCode = "403", description = "Không có quyền — lời mời không thuộc về bạn"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy lời mời")
    })
    @PatchMapping("/{invitationId}")
    @PreAuthorize("hasRole('ASSISTANT')")
    public ResponseEntity<SeriesAssistantResponse> respondToInvitation(
            @Parameter(description = "ID của lời mời", example = "10")
            @PathVariable Long invitationId,
            @Valid @RequestBody RespondInvitationRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(
                seriesAssistantService.respondToInvitation(
                        invitationId, request, currentUser));
    }
}
