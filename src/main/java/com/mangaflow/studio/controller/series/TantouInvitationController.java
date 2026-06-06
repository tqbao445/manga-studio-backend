package com.mangaflow.studio.controller.series;

import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.series.request.InviteTantouRequest;
import com.mangaflow.studio.dto.series.request.RespondInvitationRequest;
import com.mangaflow.studio.dto.series.response.SeriesTantouResponse;
import com.mangaflow.studio.service.series.SeriesTantouInvitationService;
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
 * ── TantouInvitationController ──
 * Controller quản lý lời mời TANTOU_EDITOR tham gia kiểm duyệt series.
 * <p>
 * 📌 Gồm 2 nhóm endpoint:
 * ══════════════════════════════════════════════════════════════════
 *  Nhóm 1 — MANGAKA quản lý lời mời (invite, list, remove):
 *    POST   /api/series/{seriesId}/tantou/invite
 *    GET    /api/series/{seriesId}/tantou/invitations
 *    DELETE /api/series/{seriesId}/tantou/{tantouId}
 * ══════════════════════════════════════════════════════════════════
 *  Nhóm 2 — TANTOU_EDITOR xem và phản hồi lời mời:
 *    GET   /api/tantou/invitations
 *    PATCH /api/tantou/invitations/{invitationId}
 * ══════════════════════════════════════════════════════════════════
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Tantou - Invitations", description = "Quản lý lời mời TANTOU_EDITOR kiểm duyệt series")
public class TantouInvitationController {

    private final SeriesTantouInvitationService seriesTantouInvitationService;

    // ════════════════════════════════════════════════════════════════
    // NHÓM 1 — MANGAKA: MỜI / XEM / XOÁ TANTOU
    // ════════════════════════════════════════════════════════════════

    /**
     * 1.1 — POST /api/series/{seriesId}/tantou/invite
     * <p>
     * MANGAKA mời 1 TANTOU_EDITOR vào kiểm duyệt series.
     * <p>
     * 📌 @PreAuthorize("hasRole('MANGAKA')"):
     *    Chỉ MANGAKA mới có quyền mời tantou.
     * <p>
     * 📌 Quy tắc:
     *    - 1 series chỉ có 1 tantou → nếu đã có → báo lỗi
     *    - Chỉ mời được user có role TANTOU_EDITOR
     *    - Không thể mời 1 tantou 2 lần khi đang PENDING
     *    - Nếu tantou từng REJECTED → có thể mời lại
     * <p>
     * 📌 Request body:
     *    { "tantouId": 10 }
     *
     * @param seriesId    ID của series (từ path)
     * @param request     DTO chứa tantouId
     * @param currentUser User đang đăng nhập (MANGAKA)
     * @return 201 CREATED + SeriesTantouResponse
     */
    @Operation(
            summary = "Mời Tantou",
            description = "MANGAKA gửi lời mời cho 1 TANTOU_EDITOR tham gia kiểm duyệt series. " +
                    "Chỉ được mời khi series chưa có tantou. Nếu tantou từ chối trước đó → mời lại được."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Mời thành công",
                    content = @Content(schema = @Schema(implementation = SeriesTantouResponse.class))),
            @ApiResponse(responseCode = "400", description = "User không phải TANTOU_EDITOR / đã mời rồi / series đã có tantou"),
            @ApiResponse(responseCode = "403", description = "Không có quyền — chỉ MANGAKA"),
            @ApiResponse(responseCode = "404", description = "Series hoặc User không tồn tại")
    })
    @PostMapping("/api/series/{seriesId}/tantou/invite")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<SeriesTantouResponse> inviteTantou(
            @Parameter(description = "ID của series", example = "5")
            @PathVariable Long seriesId,
            @Valid @RequestBody InviteTantouRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                seriesTantouInvitationService.inviteTantou(seriesId, request, currentUser));
    }

    /**
     * 1.2 — GET /api/series/{seriesId}/tantou/invitations
     * <p>
     * MANGAKA xem danh sách lời mời tantou của series mình.
     * <p>
     * 📌 @PreAuthorize("hasRole('MANGAKA')"):
     *    Chỉ MANGAKA mới xem được lời mời của series mình.
     * <p>
     * 📌 Trả về tất cả trạng thái: PENDING, ACCEPTED, REJECTED.
     *    Frontend dùng để hiển thị trạng thái hiện tại.
     *
     * @param seriesId    ID của series
     * @param currentUser User đang đăng nhập (MANGAKA)
     * @return 200 OK + List<SeriesTantouResponse>
     */
    @Operation(
            summary = "Xem lời mời Tantou của series",
            description = "MANGAKA xem danh sách tất cả lời mời tantou của series mình " +
                    "(PENDING, ACCEPTED, REJECTED)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Danh sách lời mời",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = SeriesTantouResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Không có quyền — chỉ MANGAKA"),
            @ApiResponse(responseCode = "404", description = "Series không tồn tại")
    })
    @GetMapping("/api/series/{seriesId}/tantou/invitations")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<List<SeriesTantouResponse>> getSeriesTantouInvitations(
            @Parameter(description = "ID của series", example = "5")
            @PathVariable Long seriesId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(
                seriesTantouInvitationService.getSeriesTantouInvitations(seriesId));
    }

    /**
     * 1.3 — DELETE /api/series/{seriesId}/tantou/{tantouId}
     * <p>
     * MANGAKA xoá / huỷ lời mời tantou khỏi series.
     * <p>
     * 📌 @PreAuthorize("hasRole('MANGAKA')"):
     *    Chỉ MANGAKA mới xoá được.
     * <p>
     * 📌 Dùng khi:
     *    - Muốn đổi tantou → xoá tantou cũ → mời tantou mới
     *    - Huỷ lời mời đang PENDING
     *    - Xoá tantou đã ACCEPTED (giải phóng tantouEditor)
     *
     * @param seriesId    ID của series
     * @param tantouId    ID của tantou cần xoá
     * @param currentUser User đang đăng nhập (MANGAKA)
     * @return 204 NO CONTENT
     */
    @Operation(
            summary = "Xoá / huỷ lời mời Tantou",
            description = "MANGAKA xoá tantou khỏi series (bất kể trạng thái nào: PENDING, ACCEPTED, REJECTED). " +
                    "Nếu tantou đang là editor của series → tự động clear tantouEditor."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Xoá thành công"),
            @ApiResponse(responseCode = "403", description = "Không có quyền — chỉ MANGAKA"),
            @ApiResponse(responseCode = "404", description = "Series hoặc Tantou không tồn tại")
    })
    @DeleteMapping("/api/series/{seriesId}/tantou/{tantouId}")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<Void> removeTantouInvitation(
            @Parameter(description = "ID của series", example = "5")
            @PathVariable Long seriesId,
            @Parameter(description = "ID của tantou", example = "10")
            @PathVariable Long tantouId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        seriesTantouInvitationService.removeTantouInvitation(seriesId, tantouId, currentUser);
        return ResponseEntity.noContent().build();
    }

    // ════════════════════════════════════════════════════════════════
    // NHÓM 2 — TANTOU_EDITOR: XEM VÀ PHẢN HỒI LỜI MỜI
    // ════════════════════════════════════════════════════════════════

    /**
     * 2.1 — GET /api/tantou/invitations
     * <p>
     * TANTOU_EDITOR xem danh sách lời mời PENDING của mình.
     * <p>
     * 📌 @PreAuthorize("hasRole('TANTOU_EDITOR')"):
     *    Chỉ TANTOU_EDITOR mới xem được lời mời của mình.
     * <p>
     * 📌 Dùng trong:
     *    - InvitationsPage (frontend): hiển thị danh sách lời mời
     *    - Notification: badge đếm số lời mời chưa đọc
     * <p>
     * 📌 Chỉ trả về PENDING:
     *    ACCEPTED và REJECTED không hiển thị (đã xử lý xong).
     *
     * @param currentUser User đang đăng nhập (TANTOU_EDITOR)
     * @return 200 OK + List<SeriesTantouResponse> danh sách PENDING
     */
    @Operation(
            summary = "Xem lời mời đang chờ",
            description = "TANTOU_EDITOR xem danh sách lời mời PENDING (chưa phản hồi) từ các MANGAKA. " +
                    "Kết quả bao gồm thông tin series và người mời để hiển thị trên InvitationsPage."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Danh sách lời mời PENDING",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = SeriesTantouResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Không có quyền — chỉ TANTOU_EDITOR")
    })
    @GetMapping("/api/tantou/invitations")
    @PreAuthorize("hasRole('TANTOU_EDITOR')")
    public ResponseEntity<List<SeriesTantouResponse>> getPendingInvitations(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(
                seriesTantouInvitationService.getPendingInvitations(currentUser));
    }

    /**
     * 2.2 — PATCH /api/tantou/invitations/{invitationId}
     * <p>
     * TANTOU_EDITOR phản hồi lời mời: chấp nhận hoặc từ chối.
     * <p>
     * 📌 @PreAuthorize("hasRole('TANTOU_EDITOR')"):
     *    Chỉ TANTOU_EDITOR mới phản hồi được lời mời.
     * <p>
     * 📌 Luồng xử lý (SeriesTantouInvitationService.respondToInvitation()):
     *    1. Kiểm tra invitation tồn tại
     *    2. Kiểm tra invitation thuộc về currentUser
     *    3. Kiểm tra invitation đang PENDING
     *    4. Kiểm tra request.status là ACCEPTED hoặc REJECTED
     *    5. Nếu ACCEPTED → gán series.tantouEditor = tantou này
     *    6. Cập nhật status + respondedAt
     * <p>
     * 📌 Request body:
     *    { "status": "ACCEPTED" }   — đồng ý
     *    { "status": "REJECTED" }   — từ chối
     *
     * @param invitationId ID của lời mời (từ path variable)
     * @param request      DTO chứa status (ACCEPTED / REJECTED)
     * @param currentUser  User đang đăng nhập (TANTOU_EDITOR)
     * @return 200 OK + SeriesTantouResponse — lời mời sau khi phản hồi
     */
    @Operation(
            summary = "Phản hồi lời mời",
            description = "TANTOU_EDITOR chấp nhận hoặc từ chối lời mời tham gia kiểm duyệt series. " +
                    "Request body: {\"status\": \"ACCEPTED\"} hoặc {\"status\": \"REJECTED\"}. " +
                    "Nếu ACCEPTED → tự động gán làm tantouEditor của series. " +
                    "Chỉ phản hồi được lời mời đang PENDING và thuộc về chính mình."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Phản hồi thành công",
                    content = @Content(schema = @Schema(implementation = SeriesTantouResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ — status null / không phải ACCEPTED/REJECTED / lời mời không ở PENDING"),
            @ApiResponse(responseCode = "403", description = "Không có quyền — lời mời không thuộc về bạn"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy lời mời")
    })
    @PatchMapping("/api/tantou/invitations/{invitationId}")
    @PreAuthorize("hasRole('TANTOU_EDITOR')")
    public ResponseEntity<SeriesTantouResponse> respondToInvitation(
            @Parameter(description = "ID của lời mời", example = "10")
            @PathVariable Long invitationId,
            @Valid @RequestBody RespondInvitationRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(
                seriesTantouInvitationService.respondToInvitation(
                        invitationId, request, currentUser));
    }
}
