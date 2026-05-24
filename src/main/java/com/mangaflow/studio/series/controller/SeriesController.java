package com.mangaflow.studio.series.controller;

import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.series.dto.request.ApproveRequest;
import com.mangaflow.studio.series.dto.request.RejectRequest;
import com.mangaflow.studio.series.dto.request.SeriesRequest;
import com.mangaflow.studio.series.dto.request.UpdateStatusRequest;
import com.mangaflow.studio.series.dto.response.SeriesResponse;
import com.mangaflow.studio.series.enums.Genre;
import com.mangaflow.studio.series.enums.SeriesSortBy;
import com.mangaflow.studio.series.enums.SeriesStatus;
import com.mangaflow.studio.series.enums.TargetDemographic;
import com.mangaflow.studio.series.service.SeriesService;
import com.mangaflow.studio.series.service.SeriesWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<SeriesResponse> create(
            @Valid @RequestBody SeriesRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(seriesService.create(request, user));
    }

    // ══════════════════════════════════════════════
    // 4. PUT /api/series/{id} — Cập nhật series
    // ══════════════════════════════════════════════

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<SeriesResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody SeriesRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(seriesService.update(id, request, user));
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
    // 6. POST /api/series/{id}/submit — Gửi duyệt
    // ══════════════════════════════════════════════

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<SeriesResponse> submitForApproval(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(seriesWorkflowService.submitForApproval(id, user));
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
    // 9. PATCH /api/series/{id}/status — Chuyển trạng thái
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
