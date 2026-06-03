package com.mangaflow.studio.service.region;

import com.mangaflow.studio.common.exception.AppException;
import com.mangaflow.studio.dto.region.mapper.RegionMapper;
import com.mangaflow.studio.dto.region.request.RegionRequest;
import com.mangaflow.studio.dto.region.request.RegionUpdateRequest;
import com.mangaflow.studio.dto.region.response.RegionResponse;
import com.mangaflow.studio.model.region.Region;
import com.mangaflow.studio.model.region.RegionType;
import com.mangaflow.studio.model.task.Task;
import com.mangaflow.studio.model.task.TaskStatus;
import com.mangaflow.studio.repository.task.TaskRepository;
import com.mangaflow.studio.repository.region.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * ── RegionService ──
 * Service xử lý toàn bộ logic nghiệp vụ liên quan đến Region (vùng vẽ trên page).
 * Là tầng trung gian giữa RegionController (API) và RegionRepository (DB).
 * <p>
 * 📌 @Service: Spring Bean — chứa business logic, quản lý transaction.
 * 📌 @RequiredArgsConstructor: Lombok — DI constructor cho tất cả field final.
 * 📌 @Transactional: Tất cả method trong class đều chạy trong transaction.
 *    (Đọc: readOnly=true, Ghi: mặc định readOnly=false)
 * <p>
 * ══════════════════════════════════════════════════════════════════
 *  Danh sách method:
 * </pre>
 *  1. getRegionsByPage(pageId)     — Lấy danh sách regions
 *  2. createRegion(pageId, req)    — Tạo region mới
 *  3. updateRegion(id, req)        — Cập nhật region
 *  4. deleteRegion(id)             — Xoá region
 *  5. reorderRegions(pageId, ids)  — Sắp xếp lại thứ tự
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RegionService {

    // ════════════════════════════════════════════════════════════════
    // DI — Các dependency được inject qua constructor
    // ════════════════════════════════════════════════════════════════

    /**
     * regionRepository: Repository chính — thao tác với bảng regions.
     * Cung cấp CRUD + method tuỳ chỉnh findByPageIdOrderBySortOrderAsc().
     */
    private final RegionRepository regionRepository;

    /**
     * taskRepository: Repository cho Task — kiểm tra tasks của region.
     */
    private final TaskRepository taskRepository;

    /**
     * regionMapper: MapStruct — chuyển đổi Region entity ↔ RegionResponse DTO.
     * Tự động map các field cùng tên, kiểu.
     */
    private final RegionMapper regionMapper;

    // ════════════════════════════════════════════════════════════════
    // DEFAULT COLORS — Màu sắc mặc định cho từng loại region
    // ════════════════════════════════════════════════════════════════

    /**
     * Map màu mặc định cho từng loại region.
     * Dùng khi client không gửi color trong request.
     * <p>
     * 📌 Bảng màu:
     *    BACKGROUND  → #4ECDC4 (xanh ngọc)
     *    CHARACTER   → #FF6B6B (đỏ hồng)
     *    TEXT        → #FFE66D (vàng)
     *    EFFECT      → #A78BFA (tím)
     *    TONE        → #6B7280 (xám)
     *    OTHER       → #6B7280 (xám)
     */
    private static final Map<RegionType, String> DEFAULT_COLORS = Map.of(
            RegionType.BACKGROUND, "#4ECDC4",
            RegionType.CHARACTER, "#FF6B6B",
            RegionType.TEXT, "#FFE66D",
            RegionType.EFFECT, "#A78BFA",
            RegionType.TONE, "#6B7280",
            RegionType.OTHER, "#6B7280"
    );

    // ════════════════════════════════════════════════════════════════
    // 1. GET REGIONS BY PAGE — Lấy danh sách regions
    // ════════════════════════════════════════════════════════════════

    /**
     * Lấy danh sách regions của 1 page, sắp xếp theo sortOrder tăng dần.
     * <p>
     * 📌 @Transactional(readOnly = true):
     *    - Không cần dirty checking → Hibernate bỏ qua cơ chế flush.
     *    - Tối ưu hiệu năng — không cần transaction write lock.
     * <p>
     * 📌 regionRepository.findByPageIdOrderBySortOrderAsc():
     *    - Query: SELECT * FROM regions WHERE page_id = ? ORDER BY sort_order ASC
     *    - Trả về List<Region> đã sắp xếp theo lớp (layer).
     * <p>
     * 📌 regionMapper::toResponse:
     *    - Method reference: map từng Region entity → RegionResponse DTO.
     *    - Chuyển đổi tự động nhờ MapStruct.
     *
     * @param pageId ID của page (lấy từ URL param)
     * @return List<RegionResponse> danh sách regions (từ dưới lên trên)
     */
    @Transactional(readOnly = true)
    public List<RegionResponse> getRegionsByPage(Long pageId) {
        // Query database → lấy tất cả regions của page, sắp xếp theo sortOrder
        return regionRepository.findByPageIdOrderBySortOrderAsc(pageId)
                .stream()                    // Chuyển List<Region> thành Stream<Region>
                .map(regionMapper::toResponse) // Map từng Region → RegionResponse
                .toList();                   // Gom lại thành List<RegionResponse>
    }

    // ════════════════════════════════════════════════════════════════
    // 2. CREATE REGION — Tạo region mới
    // ════════════════════════════════════════════════════════════════

    /**
     * Tạo region mới trên 1 page.
     * <p>
     * 📌 Quy trình:
     *    1. Tính sortOrder = max(sortOrder hiện tại) + 1
     *       - Nếu page chưa có region nào → sortOrder = 0
     *       - Region mới luôn ở trên cùng (layer cao nhất)
     *    2. Gán màu mặc định nếu client không gửi color
     *       - Dùng Map DEFAULT_COLORS tra theo regionType
     *    3. Tạo Region entity bằng Builder pattern
     *    4. Lưu vào database
     *    5. Map sang DTO và trả về
     * <p>
     * 📌 RegionRequest:
     *    DTO từ frontend — chứa: regionType, label, x, y, width, height, color.
     *    Các field bắt buộc: regionType, x, y, width, height (đã validate ở Controller).
     *
     * @param pageId  ID của page (lấy từ URL param)
     * @param request DTO từ frontend (body JSON)
     * @return RegionResponse region vừa tạo
     */
    public RegionResponse createRegion(Long pageId, RegionRequest request) {
        int maxSortOrder = regionRepository.findByPageIdOrderBySortOrderAsc(pageId)
                .stream()
                .mapToInt(Region::getSortOrder)
                .max()
                .orElse(-1);

        String color = request.getColor();
        if (color == null || color.isBlank()) {
            color = DEFAULT_COLORS.getOrDefault(request.getRegionType(), "#6B7280");
        }

        Region region = Region.builder()
                .pageId(pageId)
                .regionType(request.getRegionType())
                .label(request.getLabel())
                .x(request.getX())
                .y(request.getY())
                .width(request.getWidth())
                .height(request.getHeight())
                .color(color)
                .sortOrder(maxSortOrder + 1)
                .build();

        Region savedRegion = regionRepository.save(region);
        return regionMapper.toResponse(savedRegion);
    }

    // ════════════════════════════════════════════════════════════════
    // 3. UPDATE REGION — Cập nhật region
    // ════════════════════════════════════════════════════════════════

    /**
     * Cập nhật thông tin region.
     * Chỉ cập nhật các field không null trong request (partial update).
     * <p>
     * 📌 Quy trình:
     *    1. Tìm region theo id
     *       - Nếu không tìm thấy → throw NOT_FOUND
     *    2. Cập nhật từng field nếu request gửi lên
     *    3. Lưu lại → Hibernate tự động UPDATE những field thay đổi
     *    4. Map sang DTO và trả về
     * <p>
     * 📌 Tại sao không dùng @Transactional trên từng method?
     *    @Transactional ở class-level đã bao phủ tất cả method.
     *    method nào cần readOnly thì ghi đè.
     *
     * @param id      ID của region (lấy từ URL param)
     * @param request DTO từ frontend (body JSON) — các field null sẽ bỏ qua
     * @return RegionResponse region đã cập nhật
     * @throws AppException 404 — nếu không tìm thấy region
     */
    public RegionResponse updateRegion(Long id, RegionUpdateRequest request) {
        // ── Bước 1: Tìm region ──
        // findById trả về Optional — dùng orElseThrow để ném 404
        // Nếu không tìm thấy → "Region not found: {id}"
        Region region = regionRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Region not found: " + id));

        // ── Bước 2: Cập nhật từng field ──
        // Kiểm tra null để hỗ trợ partial update (PATCH-style)
        // Chỉ cập nhật field nào request có gửi

        // regionType: loại region (BACKGROUND, CHARACTER, ...)
        if (request.getRegionType() != null) {
            region.setRegionType(request.getRegionType());
        }

        // label: nhãn hiển thị (VD: "Nhân vật chính", "Bong bóng hội thoại")
        if (request.getLabel() != null) {
            region.setLabel(request.getLabel());
        }

        // x, y: toạ độ góc trên bên trái của region trên page
        if (request.getX() != null) {
            region.setX(request.getX());
        }
        if (request.getY() != null) {
            region.setY(request.getY());
        }

        // width, height: kích thước region
        if (request.getWidth() != null) {
            region.setWidth(request.getWidth());
        }
        if (request.getHeight() != null) {
            region.setHeight(request.getHeight());
        }

        // color: màu viền/màu nền của region (hex code: #RRGGBB)
        // Chỉ cập nhật khi không null và không rỗng
        if (request.getColor() != null && !request.getColor().isBlank()) {
            region.setColor(request.getColor());
        }

        // ── Bước 3: Lưu lại ──
        // Hibernate tự động phát hiện field nào thay đổi (dirty checking)
        // → Chỉ UPDATE những cột đã thay đổi
        Region savedRegion = regionRepository.save(region);

        // ── Bước 4: Map sang DTO và trả về ──
        return regionMapper.toResponse(savedRegion);
    }

    // ════════════════════════════════════════════════════════════════
    // 4. DELETE REGION — Xoá region (chỉ khi tất cả tasks liên quan đã DONE)
    // ════════════════════════════════════════════════════════════════

    /**
     * Xoá 1 region khỏi hệ thống.
     * Chỉ xoá được khi tất cả tasks của region đã DONE hoặc không có task nào.
     *
     * @param id ID của region cần xoá
     * @throws AppException 404 — nếu không tìm thấy region
     * @throws AppException 400 — nếu còn task chưa DONE
     */
    public void deleteRegion(Long id) {
        Region region = regionRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Region not found: " + id));

        List<Task> tasks = taskRepository.findByRegionIdOrderByAssignedAtDesc(id);
        boolean hasIncompleteTask = tasks.stream()
                .anyMatch(t -> t.getStatus() != TaskStatus.DONE);

        if (hasIncompleteTask) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Cannot delete region with incomplete tasks");
        }

        regionRepository.delete(region);
    }

    // ════════════════════════════════════════════════════════════════
    // 6. REORDER REGIONS — Sắp xếp lại regions (kéo thả)
    // ════════════════════════════════════════════════════════════════

    /**
     * Sắp xếp lại thứ tự các regions trên 1 page.
     * Frontend gửi mảng regionIds theo thứ tự mới (từ dưới lên trên).
     * <p>
     * 📌 Quy trình:
     *    1. Lấy tất cả regions trong page (đã sort theo sortOrder cũ)
     *    2. Kiểm tra số lượng: regionIds từ FE phải khớp với DB
     *    3. Với mỗi id trong mảng mới → tìm region tương ứng → gán sortOrder mới
     *    4. Lưu tất cả → trả về danh sách đã sắp xếp
     * <p>
     * 📌 Frontend gửi:
     *    PUT /api/v1/pages/{pageId}/regions/reorder
     *    { "regionIds": [5, 3, 1, 4, 2] }
     * <p>
     *    Nghĩa là: region 5 ở dưới cùng (sortOrder=0), region 2 ở trên cùng (sortOrder=4)
     * <p>
     * 📌 Mapping index → sortOrder:
     *    - regionIds[0] (dưới cùng) → sortOrder = 0
     *    - regionIds[1] → sortOrder = 1
     *    - ...
     *    - regionIds[n-1] (trên cùng) → sortOrder = n-1
     *
     * @param pageId    ID của page (lấy từ URL param)
     * @param regionIds Mảng region IDs theo thứ tự mới (từ dưới lên trên)
     * @return List<RegionResponse> danh sách regions đã sắp xếp
     * @throws AppException 400 — nếu số lượng regionIds không khớp với DB
     * @throws AppException 404 — nếu 1 region id không tồn tại
     */
    public List<RegionResponse> reorderRegions(Long pageId, List<Long> regionIds) {
        // ── Bước 1: Lấy tất cả regions trong page ──
        // findAllByPageIdOrderBySortOrderAsc — trả về đã sắp xếp theo sortOrder cũ
        List<Region> regions = regionRepository.findByPageIdOrderBySortOrderAsc(pageId);

        // ── Bước 2: Kiểm tra số lượng ──
        // Đảm bảo frontend gửi đúng số regions trong page
        // Nếu không khớp → throw BAD_REQUEST (có thể FE bug hoặc gửi thiếu)
        if (regions.size() != regionIds.size()) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Region count mismatch: expected " + regions.size() + " but got " + regionIds.size());
        }

        // ── Bước 3: Gán sortOrder mới ──
        // Duyệt mảng regionIds theo thứ tự mới
        // index = 0 → dưới cùng (sortOrder = 0)
        // index = n-1 → trên cùng (sortOrder = n-1)
        for (int i = 0; i < regionIds.size(); i++) {
            Long id = regionIds.get(i);

            // Tìm region theo id trong danh sách DB
            // region.stream().filter(...).findFirst()
            // Nếu không tìm thấy → lỗi 404 (id không tồn tại)
            Region region = regions.stream()
                    .filter(r -> r.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                            "Region not found: " + id));

            // Gán sortOrder = index hiện tại
            region.setSortOrder(i);
        }

        // ── Bước 4: Lưu tất cả regions ──
        // saveAll() — Hibernate batch UPDATE từng region
        // Vì sortOrder không có UNIQUE constraint nên không lo conflict
        regionRepository.saveAll(regions);

        // ── Bước 5: Trả về danh sách đã sắp xếp ──
        return regions.stream()
                .sorted(Comparator.comparingInt(Region::getSortOrder)) // Đảm bảo đúng thứ tự
                .map(regionMapper::toResponse)                         // Map sang DTO
                .toList();
    }
}
