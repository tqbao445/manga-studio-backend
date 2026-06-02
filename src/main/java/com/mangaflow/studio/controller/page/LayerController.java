package com.mangaflow.studio.controller.page;

import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.page.request.LayerRequest;
import com.mangaflow.studio.dto.page.response.LayerResponse;
import com.mangaflow.studio.service.page.LayerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * ── LayerController ──
 * Controller xử lý tất cả API liên quan đến Layer.
 * Layer là 1 lớp đồ hoạ chồng lên Page (giống layer trong Photoshop).
 *
 * 📌 @RestController:
 *    - @Controller + @ResponseBody → mặc định trả JSON
 *    - Spring tự động chuyển LayerResponse → JSON
 *
 * 📌 @RequestMapping("/api/v1"):
 *    - Base path cho tất cả API trong controller này
 *    - VD: GET /api/v1/pages/{pageId}/layers
 *
 * 📌 @RequiredArgsConstructor:
 *    - Lombok — tự tạo constructor cho tất cả field final
 *
 * 📌 @Tag(name = "Layers"):
 *    - Nhóm các endpoints này thành 1 nhóm "Layers" trong Swagger UI
 *
 * ══════════════════════════════════════════════════════════════════
 * DANH SÁCH API:
 * ══════════════════════════════════════════════════════════════════
 *
 * ┌──────────┬──────────────────────────────────────────┬──────────────────────────────┐
 * │ Method   │ Endpoint                                 │ Chức năng                    │
 * ├──────────┼──────────────────────────────────────────┼──────────────────────────────┤
 * │ GET      │ /pages/{pageId}/layers                   │ Danh sách layer của page     │
 * │ GET      │ /layers/{id}                             │ Chi tiết 1 layer             │
 * │ POST     │ /pages/{pageId}/layers                   │ Tạo layer mới                │
 * │ PUT      │ /layers/{id}                             │ Cập nhật layer               │
 * │ DELETE   │ /layers/{id}                             │ Xoá layer                    │
 * │ PUT      │ /layers/{id}/reorder                     │ Đổi sortOrder layer          │
 * └──────────┴──────────────────────────────────────────┴──────────────────────────────┘
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Layers", description = "Quản lý layers — layer là 1 lớp đồ hoạ chồng lên Page (giống layer trong Photoshop)")
public class LayerController {

    private final LayerService layerService;
    private final ObjectMapper objectMapper;

    // ════════════════════════════════════════════════════════════════
    // 1. GET LAYERS BY PAGE — Lấy danh sách layers của 1 page
    // ════════════════════════════════════════════════════════════════

    @Operation(
            summary = "Lấy danh sách layers của 1 page",
            description = "Trả về tất cả layers trong page, sắp xếp theo sortOrder tăng dần (dưới cùng → trên cùng)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Danh sách layers", content = @Content),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @GetMapping("/pages/{pageId}/layers")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<LayerResponse>> getLayersByPage(
            @Parameter(description = "ID của page", example = "1")
            @PathVariable Long pageId) {
        return ResponseEntity.ok(layerService.getLayersByPage(pageId));
    }

    // ════════════════════════════════════════════════════════════════
    // 2. GET LAYER BY ID — Lấy chi tiết 1 layer
    // ════════════════════════════════════════════════════════════════

    @Operation(
            summary = "Lấy chi tiết 1 layer",
            description = "Trả về thông tin chi tiết của 1 layer theo id."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thông tin layer", content = @Content),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy layer"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @GetMapping("/layers/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LayerResponse> getLayerById(
            @Parameter(description = "ID của layer", example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok(layerService.getLayerById(id));
    }

    // ════════════════════════════════════════════════════════════════
    // 3. CREATE LAYER — Tạo layer mới
    // ════════════════════════════════════════════════════════════════

    @Operation(
            summary = "Tạo layer mới (multipart: file + JSON fields)",
            description = "Tạo 1 layer mới trong page. Chỉ MANGAKA mới được tạo layer. " +
                    "sortOrder tự động gán (max + 1), không cần gửi từ frontend. " +
                    "Gửi dạng multipart/form-data với key 'file' (ảnh) và 'request' (JSON). " +
                    "Nếu không có file, có thể dùng JSON thuần (không multipart) với field fileUrl."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Layer đã tạo", content = @Content),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ (label trống...)"),
            @ApiResponse(responseCode = "403", description = "Không có quyền (chỉ MANGAKA)")
    })
    @PostMapping(value = "/pages/{pageId}/layers",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<LayerResponse> createLayer(
            @Parameter(description = "ID của page", example = "1")
            @PathVariable Long pageId,

            @Parameter(description = "File ảnh layer (jpg, png, ...)")
            @RequestParam("file") MultipartFile file,

            @Parameter(description = "Thông tin layer (JSON string): {\"label\":\"...\",\"opacity\":0.8}")
            @RequestParam("request") String requestJson,

            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails currentUser) throws JsonProcessingException {
        // Parse JSON string → LayerRequest
        LayerRequest request = objectMapper.readValue(requestJson, LayerRequest.class);
        LayerResponse response = layerService.createLayer(
                pageId, request, currentUser.getUser(), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ════════════════════════════════════════════════════════════════
    // 4. UPDATE LAYER — Cập nhật layer
    // ════════════════════════════════════════════════════════════════

    @Operation(
            summary = "Cập nhật layer",
            description = "Cập nhật thông tin layer. Hỗ trợ partial update — chỉ gửi field cần thay đổi, " +
                    "field null giữ nguyên giá trị cũ. Chỉ MANGAKA mới được cập nhật."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Layer đã cập nhật", content = @Content),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy layer"),
            @ApiResponse(responseCode = "403", description = "Không có quyền (chỉ MANGAKA)")
    })
    @PutMapping("/layers/{id}")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<LayerResponse> updateLayer(
            @Parameter(description = "ID của layer cần cập nhật", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody LayerRequest request) {
        return ResponseEntity.ok(layerService.updateLayer(id, request));
    }

    // ════════════════════════════════════════════════════════════════
    // 5. DELETE LAYER — Xoá layer
    // ════════════════════════════════════════════════════════════════

    @Operation(
            summary = "Xoá 1 layer",
            description = "Xoá layer khỏi database và xoá ảnh trên Cloudinary (nếu có). Chỉ MANGAKA mới được xoá."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã xoá thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy layer"),
            @ApiResponse(responseCode = "403", description = "Không có quyền (chỉ MANGAKA)")
    })
    @DeleteMapping("/layers/{id}")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<Void> deleteLayer(
            @Parameter(description = "ID của layer cần xoá", example = "1")
            @PathVariable Long id) {
        layerService.deleteLayer(id);
        return ResponseEntity.noContent().build();
    }

    // ════════════════════════════════════════════════════════════════
    // 6. REORDER LAYER — Đổi sortOrder (drag & drop)
    // ════════════════════════════════════════════════════════════════

    @Operation(
            summary = "Đổi sortOrder của layer (kéo thả)",
            description = "Đổi thứ tự hiển thị của layer (0 = dưới cùng). " +
                    "Dùng khi kéo thả layer trong LayerPanel. Chỉ MANGAKA mới được dùng."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Layer đã đổi sortOrder", content = @Content),
            @ApiResponse(responseCode = "400", description = "sortOrder không hợp lệ (< 0)"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy layer"),
            @ApiResponse(responseCode = "403", description = "Không có quyền (chỉ MANGAKA)")
    })
    @PutMapping("/layers/{id}/reorder")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<LayerResponse> reorderLayer(
            @Parameter(description = "ID của layer cần đổi sortOrder", example = "1")
            @PathVariable Long id,
            @Parameter(description = "sortOrder mới (0 = dưới cùng)", example = "5")
            @RequestParam int sortOrder) {
        return ResponseEntity.ok(layerService.reorderLayer(id, sortOrder));
    }
}
