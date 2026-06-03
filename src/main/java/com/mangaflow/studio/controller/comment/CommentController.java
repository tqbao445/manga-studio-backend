package com.mangaflow.studio.controller.comment;

import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.comment.request.CommentRequest;
import com.mangaflow.studio.dto.comment.request.CommentStatusRequest;
import com.mangaflow.studio.dto.comment.response.CommentResponse;
import com.mangaflow.studio.service.comment.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * ── CommentController ──
 * Controller xử lý tất cả API liên quan đến Comment (bình luận/đánh dấu trên page).
 * Là tầng giao tiếp với Frontend — nhận HTTP request, gọi Service, trả về response.
 *
 * 📌 @RestController: Spring Bean — tự động serialize response thành JSON.
 * 📌 @RequestMapping("/api/v1"): Base path — tất cả endpoint bắt đầu bằng /api/v1.
 * 📌 @RequiredArgsConstructor: Lombok — DI constructor cho CommentService.
 *
 * ══════════════════════════════════════════════════════════════════
 * DANH SÁCH API:
 * ══════════════════════════════════════════════════════════════════
 *
 * ┌────────┬─────────────────────────────────────────┬──────────────────────────┬──────────────────────────┐
 * │ Method │ Endpoint                                │ Chức năng                │ Quyền                    │
 * ├────────┼─────────────────────────────────────────┼──────────────────────────┼──────────────────────────┤
 * │ GET    │ /api/v1/pages/{pageId}/comments         │ DS comment gốc + replies │ isAuthenticated()        │
 * │ POST   │ /api/v1/pages/{pageId}/comments         │ Tạo comment mới          │ MANGAKA / TANTOU_EDITOR  │
 * │ GET    │ /api/v1/comments/{id}                   │ Chi tiết 1 comment       │ isAuthenticated()        │
 * │ POST   │ /api/v1/comments/{parentId}/replies     │ Reply vào comment        │ isAuthenticated()        │
 * │ PUT    │ /api/v1/comments/{id}                   │ Sửa nội dung             │ isAuthenticated() (*)    │
 * │ DELETE │ /api/v1/comments/{id}                   │ Xoá comment              │ isAuthenticated() (*)    │
 * │ PATCH  │ /api/v1/comments/{id}/status            │ Đổi trạng thái           │ MANGAKA / TANTOU_EDITOR  │
 * └────────┴─────────────────────────────────────────┴──────────────────────────┴──────────────────────────┘
 *
 * (*) Chỉ chính chủ (author) mới sửa/xoá được comment của mình
 *     — kiểm tra trong Service (ownership check).
 *
 * ══════════════════════════════════════════════════════════════════
 *  Tóm tắt luồng xử lý 1 request:
 * ══════════════════════════════════════════════════════════════════
 *
 *  [Frontend]
 *    │  HTTP Request (JSON + JWT token)
 *    ▼
 *  [CommentController]     ← BẠN ĐANG Ở ĐÂY
 *    │  @PreAuthorize kiểm tra role
 *    │  @Valid validate request body
 *    │  @AuthenticationPrincipal lấy user từ JWT
 *    │  Gọi CommentService
 *    ▼
 *  [CommentService]
 *    │  Xử lý logic nghiệp vụ
 *    │  Gọi CommentRepository + CommentMapper
 *    ▼
 *  [Database]
 *    │
 *    ▼
 *  Response JSON về Frontend
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Comments", description = "Quản lý comments trên page — tạo, reply, resolve, xoá")
public class CommentController {

    /**
     * commentService: Service layer — chứa toàn bộ logic nghiệp vụ comment.
     * Controller chỉ làm nhiệm vụ nhận request, gọi service, trả về response.
     * KHÔNG chứa business logic.
     */
    private final CommentService commentService;

    // ════════════════════════════════════════════════════════════════
    // 1. GET COMMENTS BY PAGE — Lấy danh sách comments
    // ════════════════════════════════════════════════════════════════

    /**
     * GET /api/v1/pages/{pageId}/comments
     *
     * 📌 Chức năng:
     *    Lấy tất cả comment GỐC (parent = null) của 1 page.
     *    Mỗi comment gốc kèm theo danh sách replies của nó.
     *
     * 📌 @PreAuthorize("isAuthenticated()"):
     *    Bất kỳ user nào đã đăng nhập đều xem được comments.
     *
     * 📌 @PathVariable Long pageId:
     *    Lấy pageId từ URL path — VD: /pages/5/comments → pageId = 5
     *
     * @param pageId ID của page (lấy từ URL)
     * @return ResponseEntity chứa danh sách comments gốc + replies (HTTP 200)
     */
    @Operation(
            summary = "Lấy danh sách comments của 1 page",
            description = "Trả về tất cả comment gốc (annotations) của page, " +
                    "mỗi comment kèm danh sách replies. " +
                    "Tất cả user đã đăng nhập đều xem được."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Danh sách comments",
                    content = @Content(schema = @Schema(implementation = CommentResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @GetMapping("/pages/{pageId}/comments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CommentResponse>> getCommentsByPage(
            @Parameter(description = "ID của page", example = "1")
            @PathVariable Long pageId) {
        // Gọi service → lấy danh sách comments → trả về HTTP 200
        return ResponseEntity.ok(commentService.getByPage(pageId));
    }

    // ════════════════════════════════════════════════════════════════
    // 2. GET COMMENT BY ID — Lấy chi tiết 1 comment
    // ════════════════════════════════════════════════════════════════

    /**
     * GET /api/v1/comments/{id}
     *
     * 📌 Chức năng:
     *    Lấy chi tiết 1 comment + replies của nó.
     *
     * @param id ID của comment (lấy từ URL)
     * @return ResponseEntity chứa CommentResponse (HTTP 200)
     */
    @Operation(
            summary = "Lấy chi tiết 1 comment + replies",
            description = "Trả về thông tin đầy đủ của 1 comment, bao gồm danh sách replies."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chi tiết comment",
                    content = @Content(schema = @Schema(implementation = CommentResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy comment"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @GetMapping("/comments/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CommentResponse> getCommentById(
            @Parameter(description = "ID của comment", example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok(commentService.getById(id));
    }

    // ════════════════════════════════════════════════════════════════
    // 3. CREATE COMMENT — Tạo comment mới
    // ════════════════════════════════════════════════════════════════

    /**
     * POST /api/v1/pages/{pageId}/comments
     *
     * 📌 Chức năng:
     *    Tạo comment mới trên 1 page.
     *    Chỉ MANGAKA và TANTOU_EDITOR mới được tạo.
     *
     * 📌 Phân biệt:
     *    - Không có parentId trong body → tạo comment gốc (annotation)
     *      (Có thể kèm toạ độ posX, posY để vẽ ô trên ảnh page)
     *    - Có parentId trong body → tạo reply (trong thread)
     *      (Không cần toạ độ)
     *
     * 📌 Request body (tạo annotation):
     *    {
     *        "content": "Sửa thoại dòng 3",
     *        "posX": 150.0,
     *        "posY": 200.0,
     *        "posWidth": 100.0,
     *        "posHeight": 60.0
     *    }
     *
     * 📌 Request body (tạo reply):
     *    {
     *        "content": "Đã sửa xong",
     *        "parentId": 1
     *    }
     *
     * @param pageId  ID của page (lấy từ URL)
     * @param request Body JSON chứa: content, parentId (tuỳ chọn), posX, posY,... (tuỳ chọn)
     * @param user    Thông tin user từ JWT token (Spring tự inject)
     * @return ResponseEntity chứa comment vừa tạo (HTTP 201)
     */
    @Operation(
            summary = "Tạo comment mới trên page",
            description = "Tạo annotation hoặc reply trên page. " +
                    "Chỉ MANGAKA và TANTOU_EDITOR mới được dùng. " +
                    "Nếu có parentId → tạo reply. " +
                    "Nếu không có parentId → tạo comment gốc (có thể kèm toạ độ)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Comment đã tạo thành công",
                    content = @Content(schema = @Schema(implementation = CommentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ (thiếu content, ...)"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy page hoặc parent comment"),
            @ApiResponse(responseCode = "403", description = "Không có quyền — chỉ MANGAKA / TANTOU_EDITOR")
    })
    @PostMapping("/pages/{pageId}/comments")
    @PreAuthorize("hasAnyRole('MANGAKA', 'TANTOU_EDITOR')")
    public ResponseEntity<CommentResponse> createComment(
            @Parameter(description = "ID của page muốn comment", example = "1")
            @PathVariable Long pageId,

            @Parameter(description = "Thông tin comment cần tạo")
            @Valid @RequestBody CommentRequest request,

            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails user) {
        // Gọi service → tạo comment mới → trả về HTTP 201 Created
        CommentResponse response = commentService.create(pageId, request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ════════════════════════════════════════════════════════════════
    // 4. REPLY — Trả lời 1 comment
    // ════════════════════════════════════════════════════════════════

    /**
     * POST /api/v1/comments/{parentId}/replies
     *
     * 📌 Chức năng:
     *    Reply vào 1 comment có sẵn.
     *    Bất kỳ user nào đã đăng nhập đều có thể reply.
     *    (Kể cả ASSISTANT — để trả lời trong thread thảo luận)
     *
     * 📌 So với POST /pages/{pageId}/comments:
     *    - Endpoint này chỉ cần parentId (comment cha) và content.
     *    - pageId được lấy tự động từ comment cha.
     *    - Không cần toạ độ (reply không phải annotation).
     *
     * @param parentId ID của comment cha (lấy từ URL path)
     * @param request  Body JSON chỉ chứa content
     * @param user     Thông tin user từ JWT token
     * @return ResponseEntity chứa reply vừa tạo (HTTP 201)
     */
    @Operation(
            summary = "Reply vào 1 comment",
            description = "Tạo reply trong thread của 1 comment. " +
                    "Bất kỳ user nào đã đăng nhập đều có thể reply. " +
                    "pageId được tự động lấy từ comment cha."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reply đã tạo thành công",
                    content = @Content(schema = @Schema(implementation = CommentResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy parent comment"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @PostMapping("/comments/{parentId}/replies")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CommentResponse> replyToComment(
            @Parameter(description = "ID của comment cha cần reply", example = "1")
            @PathVariable Long parentId,

            @Parameter(description = "Nội dung reply (chỉ cần content)")
            @Valid @RequestBody CommentRequest request,

            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails user) {
        // Gọi service → tạo reply → trả về HTTP 201 Created
        CommentResponse response = commentService.reply(parentId, request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ════════════════════════════════════════════════════════════════
    // 5. UPDATE COMMENT — Cập nhật nội dung
    // ════════════════════════════════════════════════════════════════

    /**
     * PUT /api/v1/comments/{id}
     *
     * 📌 Chức năng:
     *    Sửa nội dung comment. Chỉ AUTHOR mới được sửa.
     *    (Service kiểm tra ownership: findByIdAndAuthorId)
     *
     * 📌 Nếu không phải author:
     *    Service trả về 404 (không tiết lộ "không phải của bạn").
     *
     * @param id      ID của comment cần sửa (lấy từ URL)
     * @param request Body JSON chứa content mới
     * @param user    Thông tin user từ JWT token
     * @return ResponseEntity chứa comment đã cập nhật (HTTP 200)
     */
    @Operation(
            summary = "Cập nhật nội dung comment",
            description = "Sửa nội dung comment. Chỉ chính tác giả mới sửa được. " +
                    "Nếu không phải tác giả → trả về 404."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comment đã cập nhật",
                    content = @Content(schema = @Schema(implementation = CommentResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy comment hoặc không phải chủ"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @PutMapping("/comments/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CommentResponse> updateComment(
            @Parameter(description = "ID của comment cần sửa", example = "1")
            @PathVariable Long id,

            @Parameter(description = "Nội dung comment mới")
            @Valid @RequestBody CommentRequest request,

            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails user) {
        // Gọi service → cập nhật comment → HTTP 200
        return ResponseEntity.ok(commentService.update(id, request, user));
    }

    // ════════════════════════════════════════════════════════════════
    // 6. DELETE COMMENT — Xoá comment
    // ════════════════════════════════════════════════════════════════

    /**
     * DELETE /api/v1/comments/{id}
     *
     * 📌 Chức năng:
     *    Xoá 1 comment. Chỉ AUTHOR mới được xoá.
     *    Khi xoá comment gốc → tự động xoá luôn tất cả replies.
     *
     * 📌 Response 204:
     *    Xoá thành công → HTTP 204 No Content (không trả body).
     *
     * @param id   ID của comment cần xoá (lấy từ URL)
     * @param user Thông tin user từ JWT token
     * @return ResponseEntity rỗng (HTTP 204)
     */
    @Operation(
            summary = "Xoá comment",
            description = "Xoá comment. Chỉ tác giả mới xoá được. " +
                    "Khi xoá comment gốc → tự động xoá tất cả replies."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã xoá thành công — không có nội dung trả về"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy comment hoặc không phải chủ"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @DeleteMapping("/comments/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteComment(
            @Parameter(description = "ID của comment cần xoá", example = "1")
            @PathVariable Long id,

            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails user) {
        // Gọi service → xoá comment → HTTP 204 No Content
        commentService.delete(id, user);
        return ResponseEntity.noContent().build();
    }

    // ════════════════════════════════════════════════════════════════
    // 7. UPDATE COMMENT STATUS — Đổi trạng thái (RESOLVE / REOPEN)
    // ════════════════════════════════════════════════════════════════

    /**
     * PATCH /api/v1/comments/{id}/status
     *
     * 📌 Chức năng:
     *    Đổi trạng thái comment: ACTIVE ↔ RESOLVED.
     *    Chỉ MANGAKA và TANTOU_EDITOR mới có quyền này.
     *
     * 📌 Khi nào dùng?
     *    - ACTIVE → RESOLVED: Tantou Editor hoặc Mangaka
     *      xác nhận vấn đề đã được xử lý xong.
     *    - RESOLVED → ACTIVE: Cần mở lại để chỉnh sửa thêm.
     *
     * 📌 PATCH vs PUT:
     *    Dùng PATCH vì chỉ thay đổi 1 field (status) thay vì toàn bộ resource.
     *
     * @param id      ID của comment cần đổi trạng thái (lấy từ URL)
     * @param request Body JSON chứa status mới (ACTIVE hoặc RESOLVED)
     * @param user    Thông tin user từ JWT token
     * @return ResponseEntity chứa comment đã đổi status (HTTP 200)
     */
    @Operation(
            summary = "Đổi trạng thái comment (RESOLVE / REOPEN)",
            description = "Chuyển trạng thái comment giữa ACTIVE ↔ RESOLVED. " +
                    "ACTIVE → RESOLVED: đánh dấu đã giải quyết. " +
                    "RESOLVED → ACTIVE: mở lại để thảo luận tiếp. " +
                    "Chỉ MANGAKA và TANTOU_EDITOR mới được dùng."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comment đã đổi trạng thái",
                    content = @Content(schema = @Schema(implementation = CommentResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy comment"),
            @ApiResponse(responseCode = "403", description = "Không có quyền — chỉ MANGAKA / TANTOU_EDITOR")
    })
    @PatchMapping("/comments/{id}/status")
    @PreAuthorize("hasAnyRole('MANGAKA', 'TANTOU_EDITOR')")
    public ResponseEntity<CommentResponse> updateCommentStatus(
            @Parameter(description = "ID của comment cần đổi trạng thái", example = "1")
            @PathVariable Long id,

            @Parameter(description = "Trạng thái mới: ACTIVE (hoạt động) hoặc RESOLVED (đã giải quyết)")
            @Valid @RequestBody CommentStatusRequest request,

            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails user) {
        // Gọi service → đổi status → HTTP 200
        return ResponseEntity.ok(
                commentService.updateStatus(id, request.getStatus(), user));
    }
}
