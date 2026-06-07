package com.mangaflow.studio.controller.series;

import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.series.request.SeriesRequest;
import com.mangaflow.studio.dto.series.request.UpdateStatusRequest;
import com.mangaflow.studio.dto.series.response.SeriesResponse;
import com.mangaflow.studio.model.series.Genre;
import com.mangaflow.studio.model.series.SeriesSortBy;
import com.mangaflow.studio.model.series.SeriesStatus;
import com.mangaflow.studio.model.series.TargetDemographic;
import com.mangaflow.studio.service.series.SeriesService;
import com.mangaflow.studio.service.series.SeriesWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
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

@RestController
@RequestMapping("/api/series")
@RequiredArgsConstructor
@Tag(name = "Series", description = "API quản lý series và quy trình duyệt")
public class SeriesController {

    private final SeriesService seriesService;
    private final SeriesWorkflowService seriesWorkflowService;

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
            @RequestPart("series") @Valid SeriesRequest request,
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
            @RequestPart("series") @Valid SeriesRequest request,
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
    @PreAuthorize("hasRole('EDITORIAL_BOARD')")
    public ResponseEntity<SeriesResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(seriesWorkflowService.updateStatus(id, request, user));
    }
}
