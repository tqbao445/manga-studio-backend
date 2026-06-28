package com.mangaflow.studio.controller.series;

import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.series.request.SeriesRequest;
import com.mangaflow.studio.dto.series.request.StoryProfileRequest;
import com.mangaflow.studio.dto.series.request.UpdateStatusRequest;
import com.mangaflow.studio.dto.series.response.SeriesResponse;
import com.mangaflow.studio.dto.series.response.StoryProfileResponse;
import com.mangaflow.studio.model.series.Genre;
import com.mangaflow.studio.model.series.SeriesSortBy;
import com.mangaflow.studio.model.series.SeriesStatus;
import com.mangaflow.studio.model.series.TargetDemographic;
import com.mangaflow.studio.dto.feedback.response.ReaderFeedbackResponse;
import com.mangaflow.studio.service.feedback.ReaderFeedbackService;
import com.mangaflow.studio.service.series.SeriesService;
import com.mangaflow.studio.service.series.SeriesStoryProfileService;
import com.mangaflow.studio.service.series.SeriesWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/series")
@RequiredArgsConstructor
@Tag(name = "Series", description = "API quản lý series và quy trình duyệt")
public class SeriesController {

    private final SeriesService seriesService;
    private final SeriesStoryProfileService seriesStoryProfileService;
    private final SeriesWorkflowService seriesWorkflowService;
    private final ReaderFeedbackService readerFeedbackService;

    @Operation(summary = "Danh sách series",
               description = "Lấy danh sách series với filter theo status, genre, search và phân trang.")
    @GetMapping
    public ResponseEntity<Page<SeriesResponse>> getAll(
            @RequestParam(required = false) SeriesStatus status,
            @RequestParam(required = false) Genre genre,
            @RequestParam(required = false) TargetDemographic targetDemographic,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "UPDATED_AT_DESC") SeriesSortBy sort,
            @AuthenticationPrincipal CustomUserDetails user) {
        Pageable pageable = PageRequest.of(page, size, sort.getSort());
        return ResponseEntity.ok(seriesService.getAll(status, genre, targetDemographic, search, pageable, user));
    }

    @Operation(summary = "Chi tiết series", description = "Xem thông tin chi tiết của 1 series.")
    @GetMapping("/{id}")
    public ResponseEntity<SeriesResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(seriesService.getById(id));
    }

    @Operation(summary = "Tạo series mới",
               description = "Mangaka tạo series mới. Gửi multipart/form-data với phần 'series' (JSON) + 'file' (ảnh bìa).")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Tạo series thành công")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<SeriesResponse> create(
            /*
             * @Parameter với @Content giúp Swagger UI gửi đúng Content-Type: application/json
             * cho phần JSON. Nếu thiếu, Swagger gửi application/octet-stream gây lỗi:
             * "Content-Type 'application/octet-stream' is not supported"
             */
            @Parameter(description = "Thông tin series (JSON)",
                       content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                          schema = @Schema(implementation = SeriesRequest.class)))
            @RequestPart("series") @Valid SeriesRequest request,
            @Parameter(description = "Ảnh bìa (jpg, png, ...)")
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(seriesService.create(request, file, user));
    }

    @Operation(summary = "Cập nhật series",
               description = "Mangaka cập nhật thông tin series. File ảnh bìa là tuỳ chọn.")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<SeriesResponse> update(
            @PathVariable Long id,
            @Parameter(description = "Thông tin series (JSON)",
                       content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                          schema = @Schema(implementation = SeriesRequest.class)))
            @RequestPart("series") @Valid SeriesRequest request,
            @Parameter(description = "Ảnh bìa (jpg, png, ...) - không bắt buộc")
            @RequestParam(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(seriesService.update(id, request, file, user));
    }

    @Operation(summary = "Xoá series", description = "Mangaka xoá series (chỉ khi series đang ở DRAFT).")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        seriesService.delete(id, user);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Gửi cho Tantou Editor duyệt",
               description = "Mangaka gửi series cho Tantou Editor phê duyệt. " +
                           "Series phải ở trạng thái DRAFT và đã có tantouEditor được chỉ định.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Gửi thành công, series chuyển PENDING_TANTOU"),
        @ApiResponse(responseCode = "400", description = "Series không ở DRAFT hoặc chưa có tantouEditor")
    })
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<SeriesResponse> submitToTantou(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(seriesWorkflowService.submitToTantou(id, user));
    }

    @Operation(summary = "Tantou Editor đồng ý",
               description = "Tantou Editor đồng ý với bản thảo, chuyển series lên PENDING_BOARD_VOTE " +
                           "để chờ Chief Editor tạo cuộc họp.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Đồng ý thành công"),
        @ApiResponse(responseCode = "400", description = "Series không ở PENDING_TANTOU"),
        @ApiResponse(responseCode = "403", description = "Bạn không phải tantouEditor của series này")
    })
    @PostMapping("/{id}/tantou/approve")
    @PreAuthorize("hasRole('TANTOU_EDITOR')")
    public ResponseEntity<SeriesResponse> tantouApprove(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(seriesWorkflowService.tantouApprove(id, user));
    }

    @Operation(summary = "Tantou Editor từ chối",
               description = "Tantou Editor từ chối bản thảo, series quay về DRAFT để Mangaka sửa lại.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Từ chối thành công"),
        @ApiResponse(responseCode = "400", description = "Series không ở PENDING_TANTOU"),
        @ApiResponse(responseCode = "403", description = "Bạn không phải tantouEditor của series này")
    })
    @PostMapping("/{id}/tantou/reject")
    @PreAuthorize("hasRole('TANTOU_EDITOR')")
    public ResponseEntity<SeriesResponse> tantouReject(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(seriesWorkflowService.tantouReject(id, user));
    }

    @Operation(summary = "Chuyển trạng thái series",
               description = "Chuyển trạng thái series. Chỉ áp dụng cho ONGOING/HIATUS/AT_RISK. " +
                           "Các chuyển đổi: ONGOING ↔ HIATUS, ONGOING → AT_RISK/CANCELLED/COMPLETED, " +
                           "HIATUS → CANCELLED, AT_RISK → ONGOING/CANCELLED.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Chuyển trạng thái thành công"),
        @ApiResponse(responseCode = "400", description = "Transition không hợp lệ")
    })
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('EDITORIAL_BOARD', 'CHIEF_EDITOR')")
    public ResponseEntity<SeriesResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(seriesWorkflowService.updateStatus(id, request, user));
    }

    // ════════════════════════════════════════════════════════════════
    //  STORY PROFILE ENDPOINTS (World Lore + Roadmap + Visual Refs)
    // ════════════════════════════════════════════════════════════════

    /**
     * ── GET /api/series/{id}/story-profile ──
     * Lấy story profile của series (world lore, roadmap, visual refs).
     * Public — ai cũng xem được.
     */
    @Operation(summary = "Lấy story profile",
               description = "Lấy world lore, story roadmap và visual references của series.")
    @GetMapping("/{id}/story-profile")
    public ResponseEntity<StoryProfileResponse> getStoryProfile(@PathVariable Long id) {
        return ResponseEntity.ok(seriesStoryProfileService.getStoryProfile(id));
    }

    /**
     * ── PUT /api/series/{id}/story-profile ──
     * Cập nhật story profile của series.
     * Multipart: "storyProfile" (JSON) + "files" (ảnh visual refs mới, tuỳ chọn).
     * Chỉ MANGAKA mới được gọi.
     */
    @Operation(summary = "Cập nhật story profile",
               description = "Mangaka cập nhật world lore, story roadmap và visual references. "
                           + "Gửi multipart: storyProfile (JSON) + files (ảnh).")
    @PutMapping(value = "/{id}/story-profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<StoryProfileResponse> updateStoryProfile(
            @PathVariable Long id,
            @RequestPart("storyProfile") StoryProfileRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(
                seriesStoryProfileService.updateStoryProfile(id, request, files, user));
    }

    // ════════════════════════════════════════════════════════════════
    //  READER FEEDBACK — Phản hồi độc giả cho Survival Radar
    // ════════════════════════════════════════════════════════════════

    /**
     * ── GET /api/v1/series/{id}/reader-feedback ──
     *
     * Mục đích:
     *   Cung cấp dữ liệu cho box "Reader Feedback" trong Survival Radar
     *   của Mangaka Dashboard.
     *
     * Cách hoạt động:
     *   1. FE gọi với seriesId
     *   2. Backend kiểm tra series tồn tại
     *   3. Query ReaderFeedback WHERE seriesId = :id
     *   4. Phân loại POSITIVE → highlights, ISSUE → topIssues
     *   5. Trả về response (highlights, topIssues, summary, updatedAt)
     *
     * Business value:
     *   Mangaka biết độc giả đang nghĩ gì về series của họ mà không
     *   cần vào từng chapter. Editorial Board có thể nhập feedback
     *   từ các nguồn (survey, comment, review).
     */
    @Operation(summary = "Phản hồi độc giả (Reader Feedback)",
               description = "Lấy phản hồi nổi bật từ độc giả cho box Survival Radar. "
                           + "Trả về: highlights (lời khen), topIssues (góp ý), "
                           + "summary (tóm tắt), updatedAt.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Phản hồi độc giả"),
        @ApiResponse(responseCode = "404", description = "Series không tồn tại")
    })
    @GetMapping("/{id}/reader-feedback")
    public ResponseEntity<ReaderFeedbackResponse> getReaderFeedback(
            @Parameter(description = "ID của series", example = "5")
            @PathVariable Long id) {
        return ResponseEntity.ok(readerFeedbackService.getFeedback(id));
    }
}
