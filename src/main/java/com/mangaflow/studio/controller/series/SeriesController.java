package com.mangaflow.studio.controller.series;

import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.series.request.ApproveRequest;
import com.mangaflow.studio.dto.series.request.RejectRequest;
import com.mangaflow.studio.dto.series.request.SeriesRequest;

import com.mangaflow.studio.dto.series.request.UpdateStatusRequest;
import com.mangaflow.studio.dto.series.response.SeriesResponse;
import com.mangaflow.studio.model.series.Genre;
import com.mangaflow.studio.model.series.SeriesSortBy;
import com.mangaflow.studio.model.series.SeriesStatus;
import com.mangaflow.studio.model.series.TargetDemographic;
import com.mangaflow.studio.service.series.SeriesService;
import com.mangaflow.studio.service.series.SeriesWorkflowService;
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

@RestController
@RequestMapping("/api/series")
@RequiredArgsConstructor
public class SeriesController {

    private final SeriesService seriesService;
    private final SeriesWorkflowService seriesWorkflowService;

    // ══════════════════════════════════════════════
    // 1. GET /api/series — Danh sách series (có filter + phân trang)
    // ══════════════════════════════════════════════

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

    // ══════════════════════════════════════════════
    // 2. GET /api/series/{id} — Chi tiết 1 series
    // ══════════════════════════════════════════════

    @GetMapping("/{id}")
    public ResponseEntity<SeriesResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(seriesService.getById(id));
    }

    // ══════════════════════════════════════════════
    // 3. POST /api/series — Tạo series mới
    // ══════════════════════════════════════════════
    //
    // 📌 consumes = MULTIPART_FORM_DATA_VALUE:
    //    Client gửi multipart/form-data với 2 phần:
    //      - "series": JSON của SeriesRequest (application/json)
    //      - "file":   File ảnh bìa (image/*) — bắt buộc
    //
    // 📌 @RequestPart("series"):
    //    Spring tự parse JSON string trong multipart thành SeriesRequest object.
    //    Validation (@Valid) vẫn hoạt động bình thường.
    //
    // 📌 @RequestParam("file") MultipartFile file:
    //    Nhận file ảnh từ form-data.
    //    required = true (mặc định) → bắt buộc phải có file.
    // ══════════════════════════════════════════════

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<SeriesResponse> create(
            @RequestPart("series") @Valid SeriesRequest request,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(seriesService.create(request, file, user));
    }

    // ══════════════════════════════════════════════
    // 4. PUT /api/series/{id} — Cập nhật series
    // ══════════════════════════════════════════════
    //
    // 📌 consumes = MULTIPART_FORM_DATA_VALUE:
    //    Client gửi multipart/form-data với 2 phần:
    //      - "series": JSON của SeriesRequest (application/json)
    //      - "file":   File ảnh bìa mới (image/*) — optional
    //
    // 📌 @RequestPart("series"):
    //    Null-safe update: field null trong JSON → giữ nguyên giá trị cũ.
    //
    // 📌 @RequestParam(value = "file", required = false):
    //    Nếu không gửi file → giữ nguyên ảnh cũ.
    //    Nếu gửi file → upload ảnh mới, xoá ảnh cũ.
    // ══════════════════════════════════════════════

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<SeriesResponse> update(
            @PathVariable Long id,
            @RequestPart("series") @Valid SeriesRequest request,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(seriesService.update(id, request, file, user));
    }

    // ══════════════════════════════════════════════
    // 5. DELETE /api/series/{id} — Xoá series (chỉ DRAFT)
    // ══════════════════════════════════════════════

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        seriesService.delete(id, user);
        return ResponseEntity.noContent().build();
    }

    // ══════════════════════════════════════════════
    // 6. POST /api/series/{id}/submit — Gửi cho tantou duyệt
    // ══════════════════════════════════════════════

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<SeriesResponse> submitToTantou(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(seriesWorkflowService.submitToTantou(id, user));
    }

    // ══════════════════════════════════════════════
    // 7. POST /api/series/{id}/approve — Duyệt series
    // ══════════════════════════════════════════════

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('EDITORIAL_BOARD')")
    public ResponseEntity<SeriesResponse> approve(
            @PathVariable Long id,
            @RequestBody ApproveRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(seriesWorkflowService.approve(id, request, user));
    }

    // ══════════════════════════════════════════════
    // 8. POST /api/series/{id}/reject — Từ chối series
    // ══════════════════════════════════════════════

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('EDITORIAL_BOARD')")
    public ResponseEntity<SeriesResponse> reject(
            @PathVariable Long id,
            @RequestBody RejectRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(seriesWorkflowService.reject(id, request, user));
    }

    // ══════════════════════════════════════════════
    // 9. POST /api/series/{id}/tantou/approve — Tantou đồng ý
    // ══════════════════════════════════════════════

    @PostMapping("/{id}/tantou/approve")
    @PreAuthorize("hasRole('TANTOU_EDITOR')")
    public ResponseEntity<SeriesResponse> tantouApprove(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(seriesWorkflowService.tantouApprove(id, user));
    }

    // ══════════════════════════════════════════════
    // 10. POST /api/series/{id}/tantou/reject — Tantou từ chối
    // ══════════════════════════════════════════════

    @PostMapping("/{id}/tantou/reject")
    @PreAuthorize("hasRole('TANTOU_EDITOR')")
    public ResponseEntity<SeriesResponse> tantouReject(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(seriesWorkflowService.tantouReject(id, user));
    }

    // ══════════════════════════════════════════════
    // 11. PATCH /api/series/{id}/status — Chuyển trạng thái
    // ══════════════════════════════════════════════

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('EDITORIAL_BOARD')")
    public ResponseEntity<SeriesResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(seriesWorkflowService.updateStatus(id, request, user));
    }
}
