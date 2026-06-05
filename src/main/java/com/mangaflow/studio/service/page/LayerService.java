package com.mangaflow.studio.service.page;

import com.mangaflow.studio.common.exception.AppException;
import com.mangaflow.studio.dto.page.mapper.LayerMapper;
import com.mangaflow.studio.dto.page.request.LayerRequest;
import com.mangaflow.studio.dto.page.response.LayerResponse;
import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.model.page.Layer;
import com.mangaflow.studio.model.page.Page;
import com.mangaflow.studio.repository.page.LayerRepository;
import com.mangaflow.studio.service.storage.CloudinaryService;
import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ── LayerService ──
 * Service xử lý toàn bộ logic nghiệp vụ liên quan đến Layer.
 * Là tầng trung gian giữa LayerController (API) và LayerRepository (DB) + CloudinaryService (storage).
 *
 * 📌 @Service: Spring Bean — chứa business logic, quản lý transaction.
 * 📌 @RequiredArgsConstructor: Lombok — DI constructor cho tất cả field final.
 * 📌 @Transactional: Tất cả method trong class đều chạy trong transaction.
 *    (Đọc: readOnly=true, Ghi: mặc định readOnly=false)
 *
 * ══════════════════════════════════════════════════════════════════
 *  Luồng xử lý 1 request layer:
 * ══════════════════════════════════════════════════════════════════
 *  Frontend request
 *       │
 *       ▼
 *  LayerController (nhận HTTP, validate, inject User từ SecurityContext)
 *       │
 *       ▼
 *  LayerService (xử lý logic nghiệp vụ) ← BẠN ĐANG Ở ĐÂY
 *       │
 *       ├── LayerMapper (entity ↔ DTO)
 *       ├── CloudinaryService (xoá ảnh nếu cần)
 *       │
 *       ▼
 *  LayerRepository (lưu/truy vấn database)
 *       │
 *       ▼
 *  Response về frontend
 *
 * ══════════════════════════════════════════════════════════════════
 *  Các luồng gọi LayerService:
 * ══════════════════════════════════════════════════════════════════
 *  Luồng 1 — MANGAKA tạo layer thủ công (từ LayerPanel "Add Layer"):
 *     LayerController.createLayer() → LayerService.createLayer()
 *
 *  Luồng 2 — TaskSubmission APPROVED → tự động tạo layer:
 *     TaskService.reviewSubmission() → LayerService.createLayerFromSubmission()
 *     (Không đi qua LayerController)
 *
 *  Luồng 3 — MANGAKA chỉnh sửa layer (rename, opacity, lock...):
 *     LayerController.updateLayer() → LayerService.updateLayer()
 *
 *  Luồng 4 — MANGAKA xoá layer:
 *     LayerController.deleteLayer() → LayerService.deleteLayer()
 */
@Service
@RequiredArgsConstructor
@Transactional
public class LayerService {

    /**
     * layerRepository: Repository chính — thao tác với bảng "layer".
     * Cung cấp CRUD + các method query tuỳ chỉnh (findByPageId, findMaxSortOrder, ...).
     */
    private final LayerRepository layerRepository;

    /**
     * layerMapper: MapStruct — chuyển đổi Layer entity ↔ LayerResponse DTO.
     * Gồm: toResponse(), toEntity(), updateFromRequest().
     */
    private final LayerMapper layerMapper;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * cloudinaryService: Service xoá ảnh trên Cloudinary.
     * Dùng khi xoá layer có fileUrl (xoá ảnh trên storage trước khi xoá DB).
     */
    private final CloudinaryService cloudinaryService;

    // ════════════════════════════════════════════════════════════════
    // 1. GET LAYERS BY PAGE — Lấy danh sách layers của 1 page
    // ════════════════════════════════════════════════════════════════

    /**
     * Lấy danh sách layers của 1 page, sắp xếp theo sortOrder tăng dần
     * (layer dưới cùng → layer trên cùng).
     *
     * 📌 Query: SELECT * FROM layer WHERE page_id = ? ORDER BY sort_order ASC
     * 📌 Dùng ở: GET /api/v1/pages/{pageId}/layers
     *
     * @param pageId ID của page cần lấy layers
     * @return List<LayerResponse> danh sách layers (đã sắp xếp)
     */
    @Transactional(readOnly = true)
    public List<LayerResponse> getLayersByPage(Long pageId) {
        return layerRepository.findByPageIdOrderBySortOrderAsc(pageId)
                .stream()
                .map(layerMapper::toResponse)
                .toList();
    }

    // ════════════════════════════════════════════════════════════════
    // 2. GET LAYER BY ID — Lấy chi tiết 1 layer
    // ════════════════════════════════════════════════════════════════

    /**
     * Lấy chi tiết 1 layer theo id.
     *
     * 📌 Dùng ở: GET /api/v1/layers/{id}
     *
     * @param id ID của layer
     * @return LayerResponse thông tin chi tiết layer
     * @throws AppException 404 — nếu không tìm thấy layer
     */
    @Transactional(readOnly = true)
    public LayerResponse getLayerById(Long id) {
        Layer layer = layerRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Layer not found with id: " + id));
        return layerMapper.toResponse(layer);
    }

    // ════════════════════════════════════════════════════════════════
    // 3. CREATE LAYER — Tạo layer mới (từ frontend LayerPanel)
    // ════════════════════════════════════════════════════════════════

    /**
     * Tạo layer mới từ request của MANGAKA (từ LayerPanel "Add Layer").
     * Hỗ trợ cả 2 cách:
     *   - Upload file kèm multipart (file != null) → tự động upload Cloudinary
     *   - Chỉ gửi fileUrl string (file == null) → dùng URL có sẵn
     *
     * 📌 Quy trình:
     *    1. Map LayerRequest → Layer entity (id, createdBy, createdAt được ignore)
     *    2. Gán pageId từ param
     *    3. Nếu có file → upload lên Cloudinary → set fileUrl + thumbnailUrl
     *    4. Tính sortOrder = maxSortOrder + 1 → layer mới ở trên cùng
     *    5. Gán createdBy = user hiện tại (lấy từ SecurityContext)
     *    6. Gán createdAt = LocalDateTime.now()
     *    7. Lưu DB → map sang DTO và trả về
     *
     * @param pageId  ID của page chứa layer
     * @param request DTO từ frontend (label bắt buộc, các field khác optional)
     * @param user    User entity — người tạo layer (lấy từ SecurityContext)
     * @param file    File ảnh (optional) — nếu có, upload Cloudinary tự động
     * @return LayerResponse layer vừa tạo
     */
    public LayerResponse createLayer(Long pageId, LayerRequest request, User user,
                                      MultipartFile file) {
        // Validate label bắt buộc khi tạo
        if (request.getLabel() == null || request.getLabel().isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Label is required when creating a layer");
        }
        // Map request → entity
        Layer layer = layerMapper.toEntity(request);
        layer.setPageId(pageId);

        // Nếu có file → upload lên Cloudinary → set fileUrl + thumbnailUrl
        if (file != null && !file.isEmpty()) {
            Page page = entityManager.find(Page.class, pageId);
            if (page == null) {
                throw new AppException(HttpStatus.NOT_FOUND, "Page not found");
            }
            CloudinaryService.UploadResult uploadResult = cloudinaryService.uploadLayerImage(
                    file, user.getId(), pageId, page.getChapterId());
            layer.setFileUrl(uploadResult.getOriginalImageUrl());
            layer.setThumbnailUrl(uploadResult.getThumbnailUrl());
        }

        // Tính sortOrder tự động — layer mới luôn ở trên cùng
        int maxSortOrder = layerRepository.findMaxSortOrderByPageId(pageId);
        layer.setSortOrder(maxSortOrder + 1);

        // Set người tạo và thời gian tạo
        layer.setCreatedBy(user);
        layer.setCreatedAt(LocalDateTime.now());

        Layer savedLayer = layerRepository.save(layer);
        return layerMapper.toResponse(savedLayer);
    }

    // ════════════════════════════════════════════════════════════════
    // 4. CREATE LAYER FROM SUBMISSION (internal)
    //    — Được TaskService gọi khi MANGAKA APPROVED 1 TaskSubmission
    // ════════════════════════════════════════════════════════════════

    /**
     * Tạo layer mới từ kết quả TaskSubmission đã APPROVED.
     * Đây là method INTERNAL — chỉ TaskService gọi, KHÔNG có REST endpoint.
     *
     * 📌 Không dùng LayerRequest vì:
     *    - Các field được xác định từ logic nghiệp vụ (trusted):
     *      fileUrl = submission.resultImageUrl, label = task.title
     *
     * 📌 Khi TaskService gọi, method này chạy trong cùng transaction
     *    với reviewSubmission. Nếu createLayer lỗi → reviewSubmission rollback.
     *
     * @param pageId       ID của page chứa layer (từ task.region.pageId)
     * @param fileUrl      URL ảnh result trên Cloudinary
     * @param thumbnailUrl URL thumbnail
     * @param label        Tên hiển thị (từ task.title)
     * @param user         Người tạo (là ASSISTANT đã submit task)
     * @return LayerResponse layer vừa tạo
     */
    public LayerResponse createLayerFromSubmission(Long pageId, String fileUrl,
                                                    String thumbnailUrl, String label,
                                                    User user) {
        // Tính sortOrder — layer từ approve luôn ở trên cùng
        int maxSortOrder = layerRepository.findMaxSortOrderByPageId(pageId);
        int newSortOrder = maxSortOrder + 1;

        // Tạo entity với Builder pattern
        Layer layer = Layer.builder()
                .pageId(pageId)
                .label(label)
                .fileUrl(fileUrl)
                .thumbnailUrl(thumbnailUrl)
                .sortOrder(newSortOrder)
                .createdBy(user)
                .createdAt(LocalDateTime.now())
                .build();

        Layer savedLayer = layerRepository.save(layer);
        return layerMapper.toResponse(savedLayer);
    }

    // ════════════════════════════════════════════════════════════════
    // 5. UPDATE LAYER — Cập nhật layer (rename, opacity, lock...)
    // ════════════════════════════════════════════════════════════════

    /**
     * Cập nhật thông tin layer — hỗ trợ cập nhật từng phần (partial update).
     *
     * 📌 Quy trình:
     *    1. Tìm layer theo id → nếu không có throw 404
     *    2. Dùng LayerMapper.updateFromRequest() để merge request vào entity
     *       - Chỉ cập nhật field KHÔNG null trong request
     *       - Field null → giữ nguyên giá trị cũ
     *    3. Lưu DB → map sang DTO và trả về
     *
     * 📌 Field có thể update: label, opacity, visible, blendMode, locked, sortOrder, fileUrl
     * 📌 Field KHÔNG thể update: id, pageId, createdBy, createdAt
     *
     * @param id      ID của layer cần cập nhật
     * @param request DTO từ frontend (chỉ gửi field cần update)
     * @return LayerResponse layer sau khi cập nhật
     * @throws AppException 404 — nếu không tìm thấy layer
     */
    public LayerResponse updateLayer(Long id, LayerRequest request) {
        // Tìm layer
        Layer layer = layerRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Layer not found with id: " + id));

        // Merge request vào entity (chỉ cập nhật field ≠ null)
        layerMapper.updateFromRequest(request, layer);

        Layer updatedLayer = layerRepository.save(layer);
        return layerMapper.toResponse(updatedLayer);
    }

    // ════════════════════════════════════════════════════════════════
    // 6. DELETE LAYER — Xoá 1 layer
    // ════════════════════════════════════════════════════════════════

    /**
     * Xoá 1 layer khỏi hệ thống.
     * Bao gồm xoá ảnh trên Cloudinary (nếu có fileUrl) và xoá record trong database.
     *
     * 📌 Quy trình:
     *    1. Tìm layer theo id → nếu không có throw 404
     *    2. Xoá ảnh trên Cloudinary (nếu fileUrl != null)
     *       - Nếu Cloudinary lỗi → không throw (ưu tiên xoá DB thành công)
     *    3. Xoá record trong database
     *
     * 📌 Tại sao không throw nếu Cloudinary lỗi?
     *    Vì ưu tiên xoá layer khỏi hệ thống (DB) hơn là giữ lại layer rác.
     *    Ảnh trên Cloudinary có thể dùng cronjob dọn rác sau.
     *
     * @param id ID của layer cần xoá
     * @throws AppException 404 — nếu không tìm thấy layer
     */
    public void deleteLayer(Long id) {
        // Tìm layer
        Layer layer = layerRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Layer not found with id: " + id));

        // Xoá ảnh trên Cloudinary nếu có
        if (layer.getFileUrl() != null && !layer.getFileUrl().isBlank()) {
            try {
                cloudinaryService.deleteImageByUrl(layer.getFileUrl());
            } catch (Exception e) {
                // Không throw — ưu tiên xoá DB hơn
            }
        }

        // Xoá record trong database
        layerRepository.delete(layer);
    }

    // ════════════════════════════════════════════════════════════════
    // 7. DELETE LAYERS BY PAGE — Xoá tất cả layers của 1 page
    //    (dùng khi xoá page)
    // ════════════════════════════════════════════════════════════════

    /**
     * Xoá tất cả layers của 1 page (dùng khi xoá page).
     * Gồm xoá ảnh trên Cloudinary và xoá records trong database.
     *
     * 📌 Được gọi từ PageService.deletePage()
     *    (không có FK cascade trong DB, cần xoá layers thủ công)
     *
     * 📌 @Transactional: Toàn bộ thao tác trong 1 transaction.
     *    Nếu xoá DB lỗi → tất cả rollback.
     *
     * @param pageId ID của page cần xoá layers
     */
    public void deleteLayersByPage(Long pageId) {
        // Lấy danh sách layers
        List<Layer> layers = layerRepository.findByPageIdOrderBySortOrderAsc(pageId);

        if (layers.isEmpty()) {
            return;
        }

        // Xoá ảnh trên Cloudinary cho từng layer
        for (Layer layer : layers) {
            if (layer.getFileUrl() != null && !layer.getFileUrl().isBlank()) {
                try {
                    cloudinaryService.deleteImageByUrl(layer.getFileUrl());
                } catch (Exception e) {
                    // Không throw — tiếp tục xoá các layer khác
                }
            }
        }

        // Xoá records trong database (1 câu DELETE duy nhất)
        layerRepository.deleteByPageId(pageId);
    }

    // ════════════════════════════════════════════════════════════════
    // 8. REORDER LAYER — Đổi sortOrder cho 1 layer
    //    (dùng khi drag & drop trong LayerPanel)
    // ════════════════════════════════════════════════════════════════

    /**
     * Đổi sortOrder của 1 layer — cho tính năng kéo thả sắp xếp layer.
     *
     * 📌 Quy trình:
     *    1. Validate newSortOrder >= 0
     *    2. Tìm layer → nếu không có throw 404
     *    3. Nếu newSortOrder == old → không làm gì
     *    4. Lấy tất cả layers của cùng page
     *    5. Xoá layer khỏi list → chèn vào vị trí mới
     *    6. Pass 1: +10000 cho tất cả (tránh UNIQUE conflict)
     *    7. Pass 2: gán sortOrder thật từ 0
     *
     * 📌 Pass 1 (+10000) giống PageService.reorderPages() — tránh vi phạm
     *    UNIQUE(page_id, sort_order) khi Hibernate UPDATE không theo thứ tự.
     *
     * @param id            ID của layer cần đổi
     * @param newSortOrder  sortOrder mới (0 = dưới cùng)
     * @return LayerResponse layer sau khi cập nhật
     * @throws AppException 404 — nếu không tìm thấy layer
     * @throws AppException 400 — nếu newSortOrder < 0
     */
    public LayerResponse reorderLayer(Long id, int newSortOrder) {
        // Validate sortOrder
        if (newSortOrder < 0) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "sortOrder must be >= 0, got: " + newSortOrder);
        }

        // Tìm layer
        Layer layer = layerRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Layer not found with id: " + id));

        int oldSortOrder = layer.getSortOrder();

        // Nếu sortOrder không đổi → không làm gì
        if (oldSortOrder == newSortOrder) {
            return layerMapper.toResponse(layer);
        }

        // Lấy tất cả layers của page để sắp xếp lại
        List<Layer> pageLayers = layerRepository
                .findByPageIdOrderBySortOrderAsc(layer.getPageId());

        // Xoá layer hiện tại khỏi list
        pageLayers.removeIf(l -> l.getId().equals(id));

        // Chèn vào vị trí mới
        int insertIndex = Math.min(newSortOrder, pageLayers.size());
        pageLayers.add(insertIndex, layer);

        // Single native UPDATE with CASE — atomic, không deadlock
        Long pageId = layer.getPageId();
        StringBuilder sql = new StringBuilder("UPDATE layer SET sort_order = CASE id ");
        for (int i = 0; i < pageLayers.size(); i++) {
            sql.append("WHEN ").append(pageLayers.get(i).getId()).append(" THEN ").append(i).append(" ");
        }
        sql.append("ELSE sort_order END WHERE page_id = ").append(pageId);
        entityManager.createNativeQuery(sql.toString()).executeUpdate();

        layer.setSortOrder(insertIndex);
        return layerMapper.toResponse(layer);
    }

    // ════════════════════════════════════════════════════════════════
    // [HELPER] FIND LAYER ENTITY — Tìm entity (dùng cho service khác)
    // ════════════════════════════════════════════════════════════════

    /**
     * Tìm Layer entity theo id — trả về entity (dùng cho internal).
     *
     * 📌 Dùng cho các service khác (VD: TaskService) cần truy cập
     *    trực tiếp entity Layer (không qua DTO).
     *
     * @param id ID của layer
     * @return Layer entity
     * @throws AppException 404 — nếu không tìm thấy
     */
    @Transactional(readOnly = true)
    public Layer findLayerEntityById(Long id) {
        return layerRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Layer not found with id: " + id));
    }
}
