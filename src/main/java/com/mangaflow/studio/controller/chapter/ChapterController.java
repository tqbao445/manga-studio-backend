package com.mangaflow.studio.controller.chapter;

import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.chapter.request.ChapterRequest;
import com.mangaflow.studio.dto.chapter.response.ChapterResponse;
import com.mangaflow.studio.service.chapter.ChapterService;
import com.mangaflow.studio.service.chapter.ChapterWorkflowService;
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
 * ── ChapterController ──
 * Controller xử lý tất cả API liên quan đến Chapter (chương truyện).
 * Là tầng giao tiếp với frontend — nhận request, gọi Service, trả về response.
 * <p>
 * 📌 @RestController:
 * - @Controller + @ResponseBody → mặc định trả JSON
 * - Spring tự động chuyển ChapterResponse → JSON
 * <p>
 * 📌 @RequestMapping("/api"):
 * - Base path cho tất cả API trong controller này
 * - VD: POST /api/series/{seriesId}/chapters
 * <p>
 * 📌 @RequiredArgsConstructor:
 * - Lombok — tự tạo constructor cho tất cả field final
 * - Spring DI: tự inject ChapterService vào controller
 * <p>
 * 📌 @Tag(name = "Chapters"):
 * - Nhóm các endpoints này thành 1 nhóm "Chapters" trong Swagger UI
 * - Giúp frontend/QA dễ test API
 * <p>
 * ══════════════════════════════════════════════════════════════════
 * Luồng xử lý 1 request (tổng quan 3-layer):
 * ══════════════════════════════════════════════════════════════════
 * <p>
 * [Frontend]
 * │  HTTP Request (JSON)
 * ▼
 * [ChapterController]  ← BẠN ĐANG Ở ĐÂY
 * │  Gọi Service
 * ▼
 * [ChapterService]
 * │  Gọi Repository
 * ▼
 * [ChapterRepository] + [SeriesRepository]
 * │
 * ▼
 * [Database]
 * │
 * ▼
 * Response JSON về Frontend
 * <p>
 * ══════════════════════════════════════════════════════════════════
 * DANH SÁCH API:
 * ══════════════════════════════════════════════════════════════════
 * <p>
 * ┌──────────┬─────────────────────────────────────┬──────────────────────────────┐
 * │ Method   │ Endpoint                            │ Chức năng                    │
 * ├──────────┼─────────────────────────────────────┼──────────────────────────────┤
 * │ GET      │ /api/series/{seriesId}/chapters     │ Danh sách chapters của series│
 * │ GET      │ /api/chapters/{id}                  │ Chi tiết chapter             │
 * │ POST     │ /api/series/{seriesId}/chapters     │ Tạo chapter mới              │
 * │ PUT      │ /api/chapters/{id}                  │ Cập nhật thông tin chapter   │
 * │ DELETE   │ /api/chapters/{id}                  │ Xoá chapter (chỉ DRAFT)      │
 * └──────────┴─────────────────────────────────────┴──────────────────────────────┘
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Chapters", description = "Quản lý chapters — tạo mới, cập nhật, xoá, chuyển trạng thái")
public class ChapterController {

    /**
     * ── ChapterService ──
     * Service chứa toàn bộ logic nghiệp vụ của Chapter.
     * Controller KHÔNG chứa business logic — chỉ gọi service.
     * <p>
     * 📌 Tách biệt rõ ràng:
     * - Controller: nhận request, trả response (HTTP concerns)
     * - Service: xử lý logic, gọi repository (business concerns)
     * <p>
     * 📌 final + @RequiredArgsConstructor → Spring tự inject
     */
    private final ChapterService chapterService;

    /**
     * ── ChapterWorkflowService ──
     * Service xử lý state machine cho Chapter (submit, approve, reject, publish).
     */
    private final ChapterWorkflowService chapterWorkflowService;

    // ════════════════════════════════════════════════════════════════
    // 1. GET CHAPTERS BY SERIES — Danh sách chapters
    // ════════════════════════════════════════════════════════════════

    /**
     * GET /api/series/{seriesId}/chapters
     * <p>
     * 📌 Chức năng:
     * Lấy danh sách tất cả chapters trong một series.
     * Kết quả được sắp xếp theo chapterNumber tăng dần.
     * <p>
     * 📌 Quyền truy cập (isAuthenticated()):
     * - Tất cả user đã đăng nhập đều xem được
     * - Không cần role đặc biệt
     * <p>
     * 📌 Frontend dùng khi:
     * - Vào SeriesDetailPage → load danh sách chapters
     * - Sau khi tạo/xoá chapter → refresh lại danh sách
     *
     * @param seriesId ID của series (lấy từ URL path)
     * @return 200 OK + danh sách chapters (JSON array)
     * @response 404 — Không tìm thấy series
     */
    @Operation(
            summary = "Lấy danh sách chapters của series",
            description = "Trả về tất cả chapters trong series, sắp xếp theo chapterNumber tăng dần. Tất cả user đã đăng nhập đều xem được."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Danh sách chapters",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ChapterResponse.class)))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy series"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @GetMapping("/series/{seriesId}/chapters")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ChapterResponse>> getChaptersBySeries(
            @Parameter(description = "ID của series", example = "1")
            @PathVariable Long seriesId) {
        // Gọi service → nhận List<ChapterResponse> → wrap vào 200 OK
        return ResponseEntity.ok(chapterService.getChaptersBySeries(seriesId));
    }

    // ════════════════════════════════════════════════════════════════
    // 1B. GET CHAPTER DETAIL — Chi tiết chapter
    // ════════════════════════════════════════════════════════════════

    /**
     * GET /api/chapters/{id}
     * <p>
     * 📌 Chức năng:
     * Lấy thông tin chi tiết của một chapter theo ID.
     * Tất cả user đã đăng nhập đều xem được.
     * <p>
     * 📌 Quyền truy cập (isAuthenticated()):
     * - Tất cả role đã đăng nhập đều có thể xem
     * <p>
     * 📌 Response JSON:
     * - id, chapterNumber, title, status, seriesId, seriesTitle
     * - deadline, pageCount, progressPercent, createdAt, updatedAt
     * <p>
     * 📌 Frontend dùng khi:
     * - Hiển thị trang chi tiết chapter
     * - Load thông tin để chỉnh sửa
     *
     * @param id ID của chapter cần lấy (từ URL path)
     * @return 200 OK + ChapterResponse (JSON)
     * @response 404 — Không tìm thấy chapter
     * @response 401 — Chưa đăng nhập
     */
    @Operation(
            summary = "Lấy chi tiết chapter",
            description = "Trả về thông tin đầy đủ của một chapter theo ID. Tất cả user đã đăng nhập đều xem được."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chi tiết chapter",
                    content = @Content(schema = @Schema(implementation = ChapterResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy chapter"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @GetMapping("/chapters/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChapterResponse> getChapterById(
            @Parameter(description = "ID của chapter", example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok(chapterService.getById(id));
    }

    // ════════════════════════════════════════════════════════════════
    // 2. CREATE CHAPTER — Tạo chapter mới
    // ════════════════════════════════════════════════════════════════

    /**
     * POST /api/series/{seriesId}/chapters
     * <p>
     * 📌 Chức năng:
     * Tạo chapter mới trong một series.
     * Chỉ MANGAKA — và phải là chủ sở hữu của series — mới được tạo.
     * Chapter number phải unique trong cùng series.
     * <p>
     * 📌 Quyền truy cập (hasRole('MANGAKA')):
     * - Chỉ MANGAKA mới được tạo chapter
     * - ASSISTANT, TANTOU_EDITOR, EDITORIAL_BOARD không có quyền
     * <p>
     * 📌 Request Body (JSON):
     * - chapterNumber: Số chapter (bắt buộc, không trùng trong series)
     * - title: Tên chapter (tuỳ chọn)
     * - deadline: Hạn chót (tuỳ chọn)
     * <p>
     * 📌 Frontend dùng khi:
     * - Mangaka muốn thêm chapter mới vào series của mình
     * - Sau khi tạo, chapter ở trạng thái DRAFT
     * <p>
     * 📌 Luồng xử lý trong Service:
     * ① Kiểm tra ownership: series có thuộc về user không?
     * ② Kiểm tra unique: chapterNumber đã tồn tại chưa?
     * ③ MapStruct: ChapterRequest → Chapter entity (status = DRAFT)
     * ④ Save chapter vào database
     * ⑤ Cập nhật series.chapterCount (denormalized field)
     *
     * @param seriesId  ID của series muốn thêm chapter (từ URL path)
     * @param request   Body JSON: chapterNumber, title, deadline
     * @param user      Thông tin user từ JWT token (Spring tự inject)
     * @return 201 CREATED + ChapterResponse (JSON)
     * @response 400 — Chapter number đã tồn tại trong series này
     * @response 404 — Không tìm thấy series hoặc không phải chủ sở hữu
     * @response 403 — Không có quyền (chỉ MANGAKA)
     */
    @Operation(
            summary = "Tạo chapter mới",
            description = "Tạo chapter mới trong series. Chỉ MANGAKA (chủ sở hữu series) mới được dùng. " +
                    "Chapter number phải unique trong cùng series."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Chapter đã tạo thành công",
                    content = @Content(schema = @Schema(implementation = ChapterResponse.class))),
            @ApiResponse(responseCode = "400", description = "Chapter number đã tồn tại trong series này"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy series hoặc không phải chủ sở hữu"),
            @ApiResponse(responseCode = "403", description = "Không có quyền (chỉ MANGAKA)")
    })
    @PostMapping("/series/{seriesId}/chapters")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<ChapterResponse> createChapter(
            @Parameter(description = "ID của series muốn thêm chapter", example = "1")
            @PathVariable Long seriesId,
            @Valid @RequestBody ChapterRequest request,
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails user) {
        // Gọi Service.create() → nhận ChapterResponse → 201 Created
        ChapterResponse response = chapterService.create(seriesId, request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ════════════════════════════════════════════════════════════════
    // 3. UPDATE CHAPTER — Cập nhật thông tin chapter
    // ════════════════════════════════════════════════════════════════

    /**
     * PUT /api/chapters/{id}
     * <p>
     * 📌 Chức năng:
     * Cập nhật thông tin chapter (title, deadline, chapterNumber).
     * Chỉ MANGAKA — và phải là chủ sở hữu của series — mới được sửa.
     * <p>
     * 📌 Null-safe update:
     * Chỉ gửi field muốn đổi, field null = giữ nguyên.
     * Không thể đổi: id, series, status, pageCount, progressPercent, publishDate.
     * <p>
     * 📌 Quyền truy cập (hasRole('MANGAKA')):
     * - Chủ sở hữu series mới update được chapter
     * <p>
     * 📌 Frontend dùng khi:
     * - Mangaka muốn sửa tên chapter hoặc hạn chót
     *
     * @param id      ID của chapter cần sửa (từ URL path)
     * @param request Body JSON: chỉ gửi field muốn đổi
     * @param user    Thông tin user từ JWT token
     * @return 200 OK + ChapterResponse (đã cập nhật)
     * @response 404 — Không tìm thấy chapter hoặc không phải chủ
     */
    @Operation(
            summary = "Cập nhật thông tin chapter",
            description = "Cập nhật title, deadline, chapterNumber của chapter. " +
                    "Null-safe: chỉ gửi field muốn đổi, field null = giữ nguyên. " +
                    "Chỉ MANGAKA (chủ sở hữu series) mới được dùng."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chapter đã cập nhật",
                    content = @Content(schema = @Schema(implementation = ChapterResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy chapter hoặc không phải chủ sở hữu"),
            @ApiResponse(responseCode = "403", description = "Không có quyền (chỉ MANGAKA)")
    })
    @PutMapping("/chapters/{id}")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<ChapterResponse> updateChapter(
            @Parameter(description = "ID của chapter cần sửa", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody ChapterRequest request,
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails user) {
        // Gọi Service.update() → nhận ChapterResponse → 200 OK
        ChapterResponse response = chapterService.update(id, request, user);
        return ResponseEntity.ok(response);
    }

    // ════════════════════════════════════════════════════════════════
    // 4. DELETE CHAPTER — Xoá chapter
    // ════════════════════════════════════════════════════════════════

    /**
     * DELETE /api/chapters/{id}
     * <p>
     * 📌 Chức năng:
     * Xoá chapter khỏi hệ thống.
     * Bao gồm cập nhật lại chapterCount trong series.
     * <p>
     * 📌 Chỉ xoá được DRAFT chapter:
     * - DRAFT: có thể xoá (chưa publish, chưa ai thấy).
     * - Các status khác: không được xoá (đã có tiến độ).
     * <p>
     * 📌 Quyền truy cập (hasRole('MANGAKA')):
     * - Chủ sở hữu series mới xoá được chapter
     * <p>
     * 📌 Frontend dùng khi:
     * - Mangaka muốn xoá chapter nháp không dùng đến
     *
     * @param id   ID của chapter cần xoá
     * @param user Thông tin user từ JWT token
     * @return 204 NO CONTENT (không có body)
     * @response 404 — Không tìm thấy chapter hoặc không phải chủ
     * @response 400 — Chapter không ở trạng thái DRAFT
     */
    @Operation(
            summary = "Xoá chapter",
            description = "Xoá chapter khỏi database. Chỉ xoá được chapter ở trạng thái DRAFT. " +
                    "Chỉ MANGAKA (chủ sở hữu series) mới được dùng."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã xoá thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy chapter hoặc không phải chủ sở hữu"),
            @ApiResponse(responseCode = "400", description = "Chapter không ở trạng thái DRAFT"),
            @ApiResponse(responseCode = "403", description = "Không có quyền (chỉ MANGAKA)")
    })
    @DeleteMapping("/chapters/{id}")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<Void> deleteChapter(
            @Parameter(description = "ID của chapter cần xoá", example = "1")
            @PathVariable Long id,
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails user) {
        // Gọi Service.delete() → 204 No Content (không trả dữ liệu)
        chapterService.delete(id, user);
        return ResponseEntity.noContent().build();
    }

    // ════════════════════════════════════════════════════════════════
    // 5. CHAPTER WORKFLOW — Submit, Approve, Reject, Publish
    // ════════════════════════════════════════════════════════════════

    @Operation(summary = "Mangaka submit chapter cho Tantou review")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chapter đã submit thành công"),
            @ApiResponse(responseCode = "400", description = "Chapter không ở trạng thái DRAFT hoặc REVISION_REQUIRED"),
            @ApiResponse(responseCode = "403", description = "Không phải chủ sở hữu series")
    })
    @PostMapping("/chapters/{id}/submit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChapterResponse> submitForReview(
            @Parameter(description = "ID của chapter") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(chapterWorkflowService.submitForReview(id, user));
    }

    @Operation(summary = "Tantou approve chapter")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chapter đã được approve"),
            @ApiResponse(responseCode = "400", description = "Chapter không ở trạng thái IN_REVIEW"),
            @ApiResponse(responseCode = "403", description = "Không phải tantou editor của series")
    })
    @PostMapping("/chapters/{id}/tantou/approve")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChapterResponse> tantouApprove(
            @Parameter(description = "ID của chapter") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(chapterWorkflowService.tantouApprove(id, user));
    }

    @Operation(summary = "Tantou yêu cầu revision cho chapter")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã yêu cầu revision"),
            @ApiResponse(responseCode = "400", description = "Chapter không ở trạng thái IN_REVIEW"),
            @ApiResponse(responseCode = "403", description = "Không phải tantou editor của series")
    })
    @PostMapping("/chapters/{id}/tantou/request-revision")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChapterResponse> tantouRequestRevision(
            @Parameter(description = "ID của chapter") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(chapterWorkflowService.tantouRequestRevision(id, user));
    }

    @Operation(summary = "EB publish chapter (sau khi tantou đã approve)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chapter đã được publish"),
            @ApiResponse(responseCode = "400", description = "Chapter không ở trạng thái APPROVED hoặc series không ONGOING"),
            @ApiResponse(responseCode = "403", description = "Chỉ Editorial Board mới được publish chapter")
    })
    @PostMapping("/chapters/{id}/publish")
    @PreAuthorize("hasAnyRole('EDITORIAL_BOARD', 'CHIEF_EDITOR')")
    public ResponseEntity<ChapterResponse> publish(
            @Parameter(description = "ID của chapter") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(chapterWorkflowService.publish(id, user));
    }
}
