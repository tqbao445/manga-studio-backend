package com.mangaflow.studio.controller.region;

import com.mangaflow.studio.dto.region.request.RegionReorderRequest;
import com.mangaflow.studio.dto.region.request.RegionRequest;
import com.mangaflow.studio.dto.region.request.RegionUpdateRequest;
import com.mangaflow.studio.dto.region.response.RegionResponse;
import com.mangaflow.studio.service.region.RegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ── RegionController ──
 * Controller xử lý tất cả API liên quan đến Region (vùng vẽ trên page).
 * Là tầng giao tiếp với frontend — nhận HTTP request, gọi Service, trả về response.
 * <p>
 * 📌 @RestController: Spring Bean — tự động serialize response thành JSON.
 * 📌 @RequestMapping("/api/v1"): Base path — tất cả endpoint đều bắt đầu bằng /api/v1.
 * 📌 @RequiredArgsConstructor: Lombok — DI constructor cho RegionService.
 * <p>
 * ══════════════════════════════════════════════════════════════════
 * DANH SÁCH API:
 * ══════════════════════════════════════════════════════════════════
 * ┌────────┬──────────────────────────────────────────┬──────────────────────┬──────────┐
 * │ Method │ Endpoint                                 │ Chức năng            │ Role     │
 * ├────────┼──────────────────────────────────────────┼──────────────────────┼──────────┤
 * │ GET    │ /pages/{pageId}/regions                  │ Danh sách regions    │ ANY      │
 * │ POST   │ /pages/{pageId}/regions                  │ Tạo region mới       │ MANGAKA │
 * │ PUT    │ /regions/{id}                            │ Cập nhật region      │ MANGAKA │
 * │ PATCH  │ /regions/{id}/status                     │ Đổi trạng thái       │ MANGAKA │
 * │ DELETE │ /regions/{id}                            │ Xoá region           │ MANGAKA │
 * │ PUT    │ /pages/{pageId}/regions/reorder          │ Sắp xếp lại          │ MANGAKA │
 * └────────┴──────────────────────────────────────────┴──────────────────────┴──────────┘
 * <p>
 * 📌 Routes có /pages/{pageId} → cần pageId (thao tác theo page)
 * 📌 Routes có /regions/{id} → cần regionId (thao tác trực tiếp trên region)
 * 📌 GET: ai cũng xem được (isAuthenticated)
 * 📌 POST/PUT/PATCH/DELETE: chỉ MANGAKA mới được dùng
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Regions", description = "Quản lý regions (vùng vẽ trên page)")
public class RegionController {

    /**
     * regionService: Service layer — chứa toàn bộ logic nghiệp vụ region.
     * Controller chỉ làm nhiệm vụ nhận request, gọi service, trả về response.
     * Không chứa business logic.
     */
    private final RegionService regionService;

    // ════════════════════════════════════════════════════════════════
    // 1. GET REGIONS BY PAGE — Lấy danh sách regions
    // ════════════════════════════════════════════════════════════════

    /**
     * GET /api/v1/pages/{pageId}/regions
     * <p>
     * 📌 Chức năng: Lấy tất cả regions của 1 page, sắp xếp theo sortOrder.
     * <p>
     * 📌 Request:
     * GET /api/v1/pages/1/regions
     * <p>
     * 📌 Response 200:
     * [
     * { "id": 1, "regionType": "BACKGROUND", "x": 0, "y": 0, ... },
     * { "id": 2, "regionType": "CHARACTER", "x": 50, "y": 100, ... }
     * ]
     * <p>
     * 📌 @PreAuthorize("isAuthenticated()"):
     * - Bất kỳ user nào đã đăng nhập đều xem được
     * - Không yêu cầu role đặc biệt
     * <p>
     * 📌 @PathVariable Long pageId:
     * Lấy pageId từ URL path — VD: /pages/1/regions → pageId = 1
     *
     * @param pageId ID của page (lấy từ URL)
     * @return ResponseEntity chứa danh sách regions (HTTP 200)
     */
    @Operation(
            summary = "Lấy danh sách regions của 1 page",
            description = "Trả về tất cả regions trong page, sắp xếp theo sortOrder tăng dần (dưới cùng lên trước)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Danh sách regions"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @GetMapping("/pages/{pageId}/regions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RegionResponse>> getRegionsByPage(
            @Parameter(description = "ID của page", example = "1")
            @PathVariable Long pageId) {
        // Gọi service → lấy danh sách regions → trả về HTTP 200
        return ResponseEntity.ok(regionService.getRegionsByPage(pageId));
    }

    // ════════════════════════════════════════════════════════════════
    // 2. CREATE REGION — Tạo region mới
    // ════════════════════════════════════════════════════════════════

    /**
     * POST /api/v1/pages/{pageId}/regions
     * <p>
     * 📌 Chức năng: Tạo 1 region mới trên page.
     * Frontend gửi toạ độ, kích thước, loại region.
     * <p>
     * 📌 Request body (JSON):
     * {
     * "regionType": "CHARACTER",
     * "label": "Nhân vật chính",
     * "x": 50.0,
     * "y": 100.0,
     * "width": 200.0,
     * "height": 300.0,
     * "color": "#FF6B6B"
     * }
     * <p>
     * 📌 Response 201:
     * {
     * "id": 3,
     * "pageId": 1,
     * "regionType": "CHARACTER",
     * ...
     * "status": "PENDING",
     * "sortOrder": 0
     * }
     * <p>
     * 📌 @PreAuthorize("hasRole('MANGAKA')"):
     * - Chỉ MANGAKA mới được tạo region
     * - Nếu user không có role này → Spring trả về 403 Forbidden
     * <p>
     * 📌 @Valid @RequestBody RegionRequest:
     * - @Valid: tự động validate các annotation trên DTO
     * - Nếu validate fail → Spring trả về 400 Bad Request
     * <p>
     * 📌 ResponseEntity.status(HttpStatus.CREATED):
     * - 201 Created — chuẩn REST cho resource vừa tạo
     *
     * @param pageId  ID của page (lấy từ URL)
     * @param request DTO chứa thông tin region (body JSON)
     * @return ResponseEntity chứa region vừa tạo (HTTP 201)
     */
    @Operation(
            summary = "Tạo region mới trên page",
            description = "Tạo 1 vùng vẽ mới trên page. Chỉ MANGAKA mới được dùng."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Region đã tạo"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ (thiếu field, sai kiểu, ...)"),
            @ApiResponse(responseCode = "403", description = "Không có quyền — chỉ MANGAKA mới được tạo region")
    })
    @PostMapping("/pages/{pageId}/regions")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<RegionResponse> createRegion(
            @Parameter(description = "ID của page", example = "1")
            @PathVariable Long pageId,

            @Parameter(description = "Thông tin region cần tạo")
            @Valid @RequestBody RegionRequest request) {
        // Gọi service → tạo region mới → trả về HTTP 201 Created
        RegionResponse response = regionService.createRegion(pageId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ════════════════════════════════════════════════════════════════
    // 3. UPDATE REGION — Cập nhật region
    // ════════════════════════════════════════════════════════════════

    /**
     * PUT /api/v1/regions/{id}
     * <p>
     * 📌 Chức năng: Cập nhật thông tin region.
     * Hỗ trợ partial update — chỉ gửi các field muốn thay đổi.
     * <p>
     * 📌 Request body (JSON):
     * {
     * "label": "Tên mới",
     * "x": 100.0,
     * "y": 200.0
     * }
     * (Các field không gửi → null → không cập nhật)
     * <p>
     * 📌 Response 200:
     * {
     * "id": 1,
     * "label": "Tên mới",
     * "x": 100.0,
     * "y": 200.0,
     * ...
     * }
     * <p>
     * 📌 Route không có /pages/:
     * PUT /regions/{id} — thao tác trực tiếp trên region, không cần pageId
     * <p>
     * 📌 @PathVariable Long id:
     * ID của region — VD: /regions/1 → id = 1
     *
     * @param id      ID của region (lấy từ URL)
     * @param request DTO chứa các field muốn cập nhật (body JSON)
     * @return ResponseEntity chứa region đã cập nhật (HTTP 200)
     */
    @Operation(
            summary = "Cập nhật region",
            description = "Cập nhật thông tin region (label, type, toạ độ, kích thước, màu sắc). Hỗ trợ partial update — chỉ gửi field muốn thay đổi. Chỉ MANGAKA mới được dùng."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Region đã cập nhật"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy region với id đã cho"),
            @ApiResponse(responseCode = "403", description = "Không có quyền — chỉ MANGAKA mới được cập nhật region")
    })
    @PutMapping("/regions/{id}")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<RegionResponse> updateRegion(
            @Parameter(description = "ID của region", example = "1")
            @PathVariable Long id,

            @Parameter(description = "Thông tin region cần cập nhật (các field null sẽ bỏ qua)")
            @RequestBody RegionUpdateRequest request) {
        // Gọi service → cập nhật region → trả về HTTP 200
        return ResponseEntity.ok(regionService.updateRegion(id, request));
    }

    // ════════════════════════════════════════════════════════════════
    // 4. DELETE REGION — Xoá region (chỉ khi tất cả tasks liên quan đã DONE)
    // ════════════════════════════════════════════════════════════════

    /**
     * DELETE /api/v1/regions/{id}
     * <p>
     * 📌 Chức năng: Xoá 1 region. Chỉ xoá được khi tất cả tasks của region
     * đã DONE hoặc không có task nào.
     *
     * @param id ID của region cần xoá (lấy từ URL)
     * @return ResponseEntity rỗng (HTTP 204)
     */
    @Operation(
            summary = "Xoá region",
            description = "Xoá region. Chỉ xoá được khi tất cả tasks liên quan đã DONE. Chỉ MANGAKA mới được dùng."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã xoá thành công — không có nội dung trả về"),
            @ApiResponse(responseCode = "400", description = "Còn task chưa DONE, không thể xoá"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy region"),
            @ApiResponse(responseCode = "403", description = "Không có quyền — chỉ MANGAKA mới được xoá region")
    })
    @DeleteMapping("/regions/{id}")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<Void> deleteRegion(
            @Parameter(description = "ID của region cần xoá", example = "1")
            @PathVariable Long id) {
        regionService.deleteRegion(id);
        return ResponseEntity.noContent().build();
    }

    // ════════════════════════════════════════════════════════════════
    // 6. REORDER REGIONS — Sắp xếp lại regions
    // ════════════════════════════════════════════════════════════════

    /**
     * PUT /api/v1/pages/{pageId}/regions/reorder
     * <p>
     * 📌 Chức năng: Sắp xếp lại toàn bộ regions trên page theo thứ tự mới.
     * Dùng cho tính năng kéo thả (drag & drop) trên frontend.
     * <p>
     * 📌 Request body (JSON):
     * {
     * "regionIds": [5, 3, 1, 4, 2]
     * }
     * (regionIds[0] = dưới cùng, regionIds[n-1] = trên cùng)
     * <p>
     * 📌 Response 200:
     * [
     * { "id": 5, "sortOrder": 0, ... },
     * { "id": 3, "sortOrder": 1, ... },
     * ...
     * ]
     * <p>
     * 📌 Route đặc biệt:
     * PUT /pages/{pageId}/regions/reorder
     * - Cần pageId để lấy danh sách regions trong page
     * - "reorder" là action — đặt ở cuối path để dễ đọc
     * <p>
     * 📌 @PreAuthorize("hasRole('MANGAKA')"):
     * Chỉ MANGAKA mới được sắp xếp lại regions
     * <p>
     * 📌 @Valid @RequestBody RegionReorderRequest:
     * DTO chứa List<Long> regionIds — phải có ít nhất 1 phần tử
     *
     * @param pageId  ID của page (lấy từ URL)
     * @param request DTO chứa danh sách regionIds theo thứ tự mới
     * @return ResponseEntity chứa danh sách regions đã sắp xếp (HTTP 200)
     */
    @Operation(
            summary = "Sắp xếp lại regions (dùng cho kéo thả)",
            description = "Sắp xếp lại toàn bộ regions trên page theo thứ tự mới. Frontend gửi mảng regionIds theo thứ tự từ dưới lên trên. Chỉ MANGAKA mới được dùng."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Danh sách regions đã sắp xếp theo thứ tự mới"),
            @ApiResponse(responseCode = "400", description = "Số lượng regionIds không khớp với DB"),
            @ApiResponse(responseCode = "403", description = "Không có quyền — chỉ MANGAKA mới được sắp xếp regions")
    })
    @PutMapping("/pages/{pageId}/regions/reorder")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<List<RegionResponse>> reorderRegions(
            @Parameter(description = "ID của page", example = "1")
            @PathVariable Long pageId,

            @Parameter(description = "Danh sách regionIds theo thứ tự mới (từ dưới lên trên)")
            @Valid @RequestBody RegionReorderRequest request) {
        // Gọi service → sắp xếp lại regions → trả về HTTP 200
        return ResponseEntity.ok(
                regionService.reorderRegions(pageId, request.getRegionIds()));
    }
}
