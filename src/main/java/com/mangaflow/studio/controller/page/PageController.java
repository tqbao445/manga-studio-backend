package com.mangaflow.studio.controller.page;

import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.page.request.PageBatchReorderRequest;
import com.mangaflow.studio.dto.page.request.PageReorderRequest;
import com.mangaflow.studio.dto.page.response.PageResponse;
import com.mangaflow.studio.service.chapter.ChapterService;
import com.mangaflow.studio.service.page.PageService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * ── PageController ──
 * Controller xử lý tất cả API liên quan đến Page (trang truyện).
 * Là tầng giao tiếp với frontend — nhận request, gọi Service, trả về response.
 * <p>
 * 📌 @RestController:
 * - @Controller + @ResponseBody → mặc định trả JSON
 * - Spring tự động chuyển PageResponse → JSON
 * <p>
 * 📌 @RequestMapping("/api/v1"):
 * - Base path cho tất cả API trong controller này
 * - VD: GET /api/v1/chapters/{chapterId}/pages
 * <p>
 * 📌 @RequiredArgsConstructor:
 * - Lombok — tự tạo constructor cho tất cả field final
 * - Spring DI: tự inject PageService vào controller
 * <p>
 * 📌 @Tag(name = "Pages"):
 * - Nhóm các endpoints này thành 1 nhóm "Pages" trong Swagger UI
 * - Giúp frontend/QA dễ test API
 * <p>
 * ══════════════════════════════════════════════════════════════════
 * Luồng xử lý 1 request (tổng quan 3-layer):
 * ══════════════════════════════════════════════════════════════════
 * <p>
 * [Frontend]
 * │  HTTP Request (JSON / Multipart)
 * ▼
 * [PageController]  ← BẠN ĐANG Ở ĐÂY
 * │  Gọi Service
 * ▼
 * [PageService]
 * │  Gọi Repository + Cloudinary
 * ▼
 * [PageRepository] + [CloudinaryService]
 * │
 * ▼
 * [Database] + [Cloudinary Storage]
 * │
 * ▼
 * Response JSON về Frontend
 * <p>
 * ══════════════════════════════════════════════════════════════════
 * DANH SÁCH API:
 * ══════════════════════════════════════════════════════════════════
 * <p>
 * ┌──────────┬──────────────────────────────────────────┬──────────────────┐
 * │ Method   │ Endpoint                                 │ Chức năng        │
 * ├──────────┼──────────────────────────────────────────┼──────────────────┤
 * │ GET      │ /chapters/{chapterId}/pages              │ Danh sách pages  │
 * │ POST     │ /chapters/{chapterId}/pages              │ Upload 1 page     │
 * │ POST     │ /chapters/{chapterId}/pages/batch        │ Upload nhiều page│
 * │ DELETE   │ /pages/{id}                             │ Xoá page         │
 * │ PUT      │ /pages/{id}/order                       │ Đổi số thứ tự     │
 * │ PUT      │ /chapters/{chapterId}/pages/reorder      │ Sắp xếp lại      │
 * └──────────┴──────────────────────────────────────────┴──────────────────┘
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Pages", description = "Quản lý pages (trang truyện) — upload, import batch, sắp xếp, xoá")
public class PageController {

    /**
     * ── PageService ──
     * Service chứa toàn bộ logic nghiệp vụ của Page.
     * Controller KHÔNG chứa business logic — chỉ gọi service.
     * <p>
     * 📌 Tách biệt rõ ràng:
     * - Controller: nhận request, trả response (HTTP concerns)
     * - Service: xử lý logic, gọi repository (business concerns)
     * <p>
     * 📌 final + @RequiredArgsConstructor → Spring tự inject
     */
    private final PageService pageService;
    private final ChapterService chapterService;

    // ════════════════════════════════════════════════════════════════
    // 1. GET PAGES BY CHAPTER — Lấy danh sách pages
    // ════════════════════════════════════════════════════════════════

    /**
     * GET /api/v1/chapters/{chapterId}/pages
     * <p>
     * 📌 Chức năng:
     * Lấy danh sách tất cả pages trong 1 chapter.
     * Kết quả được sắp xếp theo pageNumber tăng dần.
     * <p>
     * 📌 Quyền truy cập (isAuthenticated()):
     * - Tất cả user đã đăng nhập đều xem được
     * - Không cần role đặc biệt
     * <p>
     * 📌 Frontend dùng khi:
     * - Vào ChapterDetailPage → load danh sách pages
     * - Sau khi upload/reorder → refresh lại danh sách
     *
     * @param chapterId ID của chapter (lấy từ URL path)
     * @return 200 OK + danh sách pages (JSON array)
     * @response 401 — Chưa đăng nhập
     */
    @Operation(
            summary = "Lấy danh sách pages của 1 chapter",
            description = "Trả về tất cả pages trong chapter, sắp xếp theo pageNumber tăng dần. Tất cả user đã đăng nhập đều xem được."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Danh sách pages"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @GetMapping("/chapters/{chapterId}/pages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PageResponse>> getPagesByChapter(
            @Parameter(description = "ID của chapter", example = "1")
            @PathVariable Long chapterId) {
        // Gọi service → nhận List<PageResponse> → wrap vào 200 OK
        return ResponseEntity.ok(pageService.getPagesByChapter(chapterId));
    }

    // ════════════════════════════════════════════════════════════════
    // 2. UPLOAD SINGLE PAGE — Upload 1 page
    // ════════════════════════════════════════════════════════════════

    /**
     * POST /api/v1/chapters/{chapterId}/pages
     * <p>
     * 📌 Chức năng:
     * Upload 1 file ảnh lên Cloudinary và tạo page record trong DB.
     * Người dùng tự chỉ định pageNumber (số thứ tự).
     * <p>
     * 📌 Quyền truy cập (hasRole('MANGAKA')):
     * - Chỉ MANGAKA mới được upload page
     * - ASSISTANT, TANTOU_EDITOR, EDITORIAL_BOARD không có quyền
     * <p>
     * 📌 Multipart form-data:
     * - file: File ảnh (jpg, png, ...) — chọn từ máy
     * - pageNumber: Số thứ tự (VD: 5 → page thứ 5 trong chapter)
     * <p>
     * 📌 Frontend dùng khi:
     * - Upload page đơn lẻ (chèn vào vị trí cụ thể)
     * - Thay vì upload batch (nếu chỉ có 1 file)
     *
     * @param chapterId  ID của chapter
     * @param file       File ảnh từ form-data (multipart)
     * @param pageNumber Số thứ tự do người dùng chỉ định
     * @param user       Thông tin user từ JWT token (Spring tự inject)
     * @return 201 CREATED + PageResponse (JSON)
     * @response 400 — Page number đã tồn tại trong chapter này
     * @response 403 — Không có quyền (chỉ MANGAKA)
     */
    @Operation(
            summary = "Upload 1 page",
            description = "Upload 1 file ảnh lên Cloudinary và tạo page record trong database. Chỉ MANGAKA mới được dùng."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Page đã tạo"),
            @ApiResponse(responseCode = "400", description = "Page number đã tồn tại trong chapter này"),
            @ApiResponse(responseCode = "403", description = "Không có quyền (chỉ MANGAKA)")
    })
    @PostMapping(value = "/chapters/{chapterId}/pages",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<PageResponse> uploadPage(
            @Parameter(description = "ID của chapter", example = "1")
            @PathVariable Long chapterId,
            @Parameter(description = "File ảnh page (jpg, png, ...)")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Số thứ tự page trong chapter", example = "1")
            @RequestParam("pageNumber") Integer pageNumber,
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails user) {
        // Gọi service upload → nhận response → 201 Created
        PageResponse response = pageService.uploadPage(
                user.getUserId(), getSeriesIdFromChapter(chapterId),
                chapterId, file, pageNumber);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ════════════════════════════════════════════════════════════════
    // 3. UPLOAD BATCH PAGES — Import nhiều page 1 lúc
    // ════════════════════════════════════════════════════════════════

    /**
     * POST /api/v1/chapters/{chapterId}/pages/batch
     * <p>
     * 📌 Chức năng:
     * Upload nhiều file ảnh cùng 1 lúc.
     * KHÔNG cần gửi pageNumbers — backend tự động gán số thứ tự
     * dựa trên max(pageNumber) hiện tại trong chapter.
     * <p>
     * 📌 Cách auto-assign pageNumber:
     * - Lấy page lớn nhất trong chapter (VD: đang có page 1,2,3)
     * - File đầu tiên → pageNumber = 4
     * - File thứ hai → pageNumber = 5
     * - File thứ ba → pageNumber = 6
     * - ...
     * <p>
     * 📌 Quyền truy cập (hasRole('MANGAKA')):
     * - Chỉ MANGAKA mới được upload
     * <p>
     * 📌 Frontend dùng khi:
     * - Import nhiều ảnh cùng lúc từ máy tính
     * - Dùng <input type="file" multiple> để chọn nhiều file
     * - Sau đó dùng API reorder để sắp xếp lại nếu cần
     * <p>
     * 📌 Multipart form-data:
     * - files[]: Mảng các file ảnh (chọn nhiều cùng lúc)
     * - Không cần gửi pageNumbers
     *
     * @param chapterId ID của chapter
     * @param files     Mảng các file ảnh (multipart)
     * @param user      Thông tin user từ JWT
     * @return 201 CREATED + danh sách PageResponse
     * @response 403 — Không có quyền (chỉ MANGAKA)
     */
    @Operation(
            summary = "Import nhiều pages cùng lúc",
            description = "Upload nhiều file ảnh cùng 1 lúc. Backend tự động gán pageNumber theo thứ tự files (lấy max + 1 từ chapter). Chỉ MANGAKA mới được dùng."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Danh sách pages đã tạo"),
            @ApiResponse(responseCode = "403", description = "Không có quyền (chỉ MANGAKA)")
    })
    @PostMapping(value = "/chapters/{chapterId}/pages/batch",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<List<PageResponse>> uploadPagesBatch(
            @Parameter(description = "ID của chapter", example = "1")
            @PathVariable Long chapterId,
            @Parameter(
                    description = "Mảng các file ảnh (chọn nhiều file cùng lúc, backend tự gán pageNumber)",
                    content = @Content(array = @ArraySchema(schema = @Schema(type = "string", format = "binary")))
            )
            @RequestParam("files") List<MultipartFile> files,
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails user) {
        // Gọi service upload batch → nhận list response → 201 Created
        List<PageResponse> responses = pageService.uploadPagesBatch(
                user.getUserId(), getSeriesIdFromChapter(chapterId),
                chapterId, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    // ════════════════════════════════════════════════════════════════
    // 4. DELETE PAGE — Xoá page
    // ════════════════════════════════════════════════════════════════

    /**
     * DELETE /api/v1/pages/{id}
     * <p>
     * 📌 Chức năng:
     * Xoá 1 page khỏi hệ thống.
     * Bao gồm:
     * 1. Xoá ảnh trên Cloudinary
     * 2. Xoá record trong database
     * <p>
     * 📌 Quyền truy cập (hasRole('MANGAKA')):
     * - Chỉ MANGAKA mới được xoá page
     * <p>
     * 📌 Frontend dùng khi:
     * - User nhấn nút xoá page trong danh sách
     * - Cần confirm trước khi gọi (tránh xoá nhầm)
     *
     * @param id ID của page cần xoá
     * @return 204 NO CONTENT (không có body)
     * @response 404 — Không tìm thấy page
     * @response 403 — Không có quyền (chỉ MANGAKA)
     */
    @Operation(
            summary = "Xoá 1 page",
            description = "Xoá page khỏi database và xoá ảnh trên Cloudinary. Chỉ MANGAKA mới được dùng."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã xoá thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy page"),
            @ApiResponse(responseCode = "403", description = "Không có quyền (chỉ MANGAKA)")
    })
    @DeleteMapping("/pages/{id}")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<Void> deletePage(
            @Parameter(description = "ID của page cần xoá", example = "1")
            @PathVariable Long id) {
        // Gọi service xoá → 204 No Content (không trả dữ liệu)
        pageService.deletePage(id);
        return ResponseEntity.noContent().build();
    }

    // ════════════════════════════════════════════════════════════════
    // 5. UPDATE SINGLE PAGE ORDER — Đổi số thứ tự 1 page
    // ════════════════════════════════════════════════════════════════

    /**
     * PUT /api/v1/pages/{id}/order
     * <p>
     * 📌 Chức năng:
     * Đổi số thứ tự của 1 page.
     * Nếu số mới đã có page khác → service tự động đẩy các page
     * bị ảnh hưởng lên/xuống 1 đơn vị.
     * <p>
     * VD: Chapter có pages: [page10-số1, page20-số2, page30-số3]
     * → Đổi page10 từ số 1 sang số 3
     * → Kết quả: [page20-số1, page30-số2, page10-số3]
     * <p>
     * 📌 Quyền truy cập (hasRole('MANGAKA')):
     * - Chỉ MANGAKA mới được đổi số thứ tự
     * <p>
     * 📌 Frontend dùng khi:
     * - Kéo thả 1 page đến vị trí mới
     * - Hoặc nhập số tay (edit pageNumber)
     *
     * @param id      ID của page cần đổi số
     * @param request Body JSON: { "pageNumber": 3 }
     * @return 200 OK + PageResponse (đã cập nhật)
     * @response 404 — Không tìm thấy page
     * @response 403 — Không có quyền (chỉ MANGAKA)
     */
    @Operation(
            summary = "Đổi số thứ tự 1 page",
            description = "Đổi số thứ tự của 1 page. Nếu số mới đã có page khác, backend tự động đẩy các page đó lên/xuống. Chỉ MANGAKA mới được dùng."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page đã cập nhật số thứ tự"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy page"),
            @ApiResponse(responseCode = "403", description = "Không có quyền (chỉ MANGAKA)")
    })
    @PutMapping("/pages/{id}/order")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<PageResponse> updateOrder(
            @Parameter(description = "ID của page cần đổi số", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody PageReorderRequest request) {
        // Gọi service → nhận page đã cập nhật → 200 OK
        return ResponseEntity.ok(pageService.updateOrder(id, request.getPageNumber()));
    }

    // ════════════════════════════════════════════════════════════════
    // 6. BATCH REORDER PAGES — Sắp xếp lại pages (kéo thả nhiều page)
    // ════════════════════════════════════════════════════════════════

    /**
     * PUT /api/v1/chapters/{chapterId}/pages/reorder
     * <p>
     * 📌 Chức năng:
     * Sắp xếp lại TOÀN BỘ pages trong chapter theo thứ tự mới.
     * Frontend gửi mảng pageIds theo thứ tự sau khi kéo thả.
     * <p>
     * VD: Chapter có 3 pages: p1(id=10), p2(id=20), p3(id=30)
     * → Người dùng kéo p3 lên đầu
     * → Frontend gửi: { "pageIds": [30, 10, 20] }
     * → Backend cập nhật:
     * page(30).pageNumber = 1
     * page(10).pageNumber = 2
     * page(20).pageNumber = 3
     * <p>
     * 📌 Quyền truy cập (hasRole('MANGAKA')):
     * - Chỉ MANGAKA mới được sắp xếp
     * <p>
     * 📌 Frontend dùng khi:
     * - Kéo thả nhiều page để sắp xếp lại toàn bộ chapter
     * - Sau khi drag-and-drop, gửi lại toàn bộ thứ tự mới
     * <p>
     * 📌 So sánh với updateOrder (PUT /pages/{id}/order):
     * - updateOrder: đổi số 1 page, các page khác tự động shift
     * - reorderPages: gán lại toàn bộ số từ đầu (dùng cho kéo thả)
     *
     * @param chapterId ID của chapter
     * @param request   Body JSON: { "pageIds": [30, 10, 20] }
     * @return 200 OK + danh sách pages sau khi sắp xếp
     * @response 400 — Số lượng pageIds không khớp với tổng số pages trong chapter
     * @response 403 — Không có quyền (chỉ MANGAKA)
     */
    @Operation(
            summary = "Batch reorder pages (dùng cho kéo thả)",
            description = "Sắp xếp lại toàn bộ pages trong chapter theo thứ tự mới. Frontend gửi mảng pageIds theo thứ tự sau khi kéo thả. VD: gửi [3,1,2] nghĩa là page 3 thành số 1, page 1 thành số 2, page 2 thành số 3."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Danh sách pages đã sắp xếp lại"),
            @ApiResponse(responseCode = "400", description = "Số lượng pageIds không khớp với tổng số pages trong chapter"),
            @ApiResponse(responseCode = "403", description = "Không có quyền (chỉ MANGAKA)")
    })
    @PutMapping("/chapters/{chapterId}/pages/reorder")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<List<PageResponse>> reorderPages(
            @Parameter(description = "ID của chapter", example = "1")
            @PathVariable Long chapterId,
            @Valid @RequestBody PageBatchReorderRequest request) {
        // Gọi service → nhận list pages đã sắp xếp → 200 OK
        return ResponseEntity.ok(
                pageService.reorderPages(chapterId, request.getPageIds()));
    }

    // ════════════════════════════════════════════════════════════════
    // 7. MERGE LAYERS — Composite layers → final image
    // ════════════════════════════════════════════════════════════════

    /**
     * POST /api/v1/pages/{id}/merge
     * <p>
     * 📌 Chức năng:
     * Merge tất cả visible layers của 1 page thành 1 ảnh duy nhất.
     * Kết quả upload lên Cloudinary, URL lưu vào page.finalImageUrl.
     * <p>
     * 📌 Quyền truy cập (hasRole('MANGAKA')):
     * - Chỉ MANGAKA mới được merge layers
     * <p>
     * 📌 Quy trình backend:
     *    1. Lấy page + layers (visible, có fileUrl)
     *    2. Download ảnh nền (page.originalImageUrl)
     *    3. Với mỗi layer: download ảnh → composite với opacity
     *    4. Upload ảnh đã merge lên Cloudinary (overwrite)
     *    5. Lưu URL merge vào page.finalImageUrl
     * <p>
     * 📌 Frontend gọi API này khi:
     *    MANGAKA bấm nút "Merge & Export" trên workspace toolbar.
     *    → Ảnh sau merge dùng để xuất file hoặc preview.
     *
     * @param id   ID của page cần merge
     * @param user Thông tin user từ JWT token (Spring tự inject)
     * @return 200 OK + PageResponse (có finalImageUrl)
     * @response 403 — Không có quyền (chỉ MANGAKA)
     * @response 500 — Lỗi composite hoặc upload Cloudinary
     */
    @Operation(
            summary = "Merge layers into final image",
            description = "Composite tất cả visible layers của page thành 1 ảnh duy nhất. Upload lên Cloudinary và lưu URL vào page.finalImageUrl."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Merge thành công — trả về page với finalImageUrl"),
            @ApiResponse(responseCode = "403", description = "Không có quyền (chỉ MANGAKA)"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy page"),
            @ApiResponse(responseCode = "500", description = "Lỗi composite hoặc upload Cloudinary")
    })
    @PostMapping("/pages/{id}/merge")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<PageResponse> mergeLayers(
            @Parameter(description = "ID của page cần merge", example = "1")
            @PathVariable Long id,
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails user) {
        PageResponse response = pageService.mergeLayers(id, user.getUserId());
        return ResponseEntity.ok(response);
    }

    // ════════════════════════════════════════════════════════════════
    // PRIVATE HELPER — Lấy seriesId từ chapterId
    // ════════════════════════════════════════════════════════════════

    /**
     * Helper tạm thời để lấy seriesId từ chapterId.
     * <p>
     * 📌 Hiện tại chưa có Chapter entity nên chưa thể truy vấn
     * seriesId từ chapterId. Tạm thời hardcode 0L.
     * <p>
     * 📌 Khi nào có ChapterService:
     * → chapterService.getChapterById(chapterId).getSeriesId()
     * <p>
     * ⚠️ TODO: Cần cập nhật sau khi có Chapter module
     */
    private Long getSeriesIdFromChapter(Long chapterId) {
        return chapterService.getSeriesIdByChapterId(chapterId);
    }
}
