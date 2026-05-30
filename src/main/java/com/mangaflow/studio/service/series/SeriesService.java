package com.mangaflow.studio.service.series;

import com.mangaflow.studio.common.exception.AppException;
import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.series.mapper.SeriesMapper;
import com.mangaflow.studio.dto.series.request.SeriesRequest;
import com.mangaflow.studio.dto.series.response.SeriesResponse;
import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.model.series.Genre;
import com.mangaflow.studio.model.series.Series;
import com.mangaflow.studio.model.series.SeriesStatus;
import com.mangaflow.studio.model.series.TargetDemographic;
import com.mangaflow.studio.repository.auth.UserRepository;
import com.mangaflow.studio.repository.series.SeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ── SeriesService ──
 * Service chịu trách nhiệm các thao tác Query và Command cơ bản cho Series.
 *
 * 📌 @Service: Spring Bean — chứa business logic, quản lý transaction.
 * 📌 @RequiredArgsConstructor: Lombok — DI constructor (final fields).
 *
 * ══════════════════════════════════════════════════
 *  Scope của Service này (Query + Command):
 * ══════════════════════════════════════════════════
 *  ✅ getAll()      — Query: danh sách series với filter + phân trang
 *  ✅ getById()     — Query: chi tiết 1 series
 *  ✅ create()      — Command: tạo mới series (MANGAKA)
 *  ✅ update()      — Command: cập nhật series (chủ sở hữu)
 *  ✅ delete()      — Command: xoá series (chủ sở hữu, DRAFT)
 *
 *  ❌ submitForApproval() → SeriesWorkflowService
 *  ❌ approve()            → SeriesWorkflowService
 *  ❌ reject()             → SeriesWorkflowService
 *  ❌ updateStatus()       → SeriesWorkflowService
 *
 * 📌 SeriesMapper (MapStruct):
 *    - toEntity():      SeriesRequest → Series entity (create)
 *    - updateEntity():  SeriesRequest → merge vào Series entity (update, null-safe)
 *    - toResponse():    Series entity → SeriesResponse DTO
 *
 * 📌 SeriesSpecification.buildFilter():
 *    Static utility — build WHERE clause động không cần viết repository method.
 */
@Service
@RequiredArgsConstructor
public class SeriesService {

    // ────────────────────────────────────────────────────────────
    // Repository & Dependencies
    // ────────────────────────────────────────────────────────────

    /**
     * seriesRepository: Repository chính — thao tác với bảng series.
     * Cung cấp CRUD + JpaSpecificationExecutor cho Specification query.
     */
    private final SeriesRepository seriesRepository;

    /**
     * userRepository: Dùng để tìm User entity (mangaka).
     * Cần thiết khi tạo series — gán mangaka bằng user đang đăng nhập.
     */
    private final UserRepository userRepository;

    /**
     * seriesMapper: MapStruct — chuyển đổi giữa Entity và DTO.
     *   - toResponse():   Series → SeriesResponse
     *   - toEntity():     SeriesRequest → Series (create)
     *   - updateEntity(): SeriesRequest → merge Series (update, null-safe)
     */
    private final SeriesMapper seriesMapper;

    // ════════════════════════════════════════════════════════════
    // 1. GET ALL — Danh sách series (có filter + phân trang)
    // ════════════════════════════════════════════════════════════

    /**
     * Lấy danh sách series với filter động và phân trang.
     *
     * 📌 @Transactional(readOnly = true):
     *    - Không cần dirty checking → Hibernate bỏ qua cơ chế flush.
     *    - Tối ưu hiệu năng đọc — không cần transaction write lock.
     *
     * 📌 SeriesSpecification.buildFilter():
     *    Static method — build Specification (WHERE clause) động.
     *    Filter theo status, genre, search, role-based access.
     *
     * 📌 seriesMapper::toResponse:
     *    Method reference — map từng Series entity → SeriesResponse DTO.
     *
     * @param status   filter theo trạng thái (optional, ví dụ "ONGOING")
     * @param genre    filter theo thể loại (optional, ví dụ "ACTION")
     * @param search   tìm kiếm theo title (optional, LIKE %search%)
     * @param pageable thông tin phân trang (page, size, sort)
     * @param user     user hiện tại (từ JWT — cho role-based filtering)
     * @return Page<SeriesResponse> trang kết quả
     */
    @Transactional(readOnly = true)
    public Page<SeriesResponse> getAll(SeriesStatus status, Genre genre,
                                        TargetDemographic targetDemographic,
                                        String search, Pageable pageable,
                                        CustomUserDetails user) {
        return seriesRepository.findAll(
                SeriesSpecification.buildFilter(status, genre, targetDemographic, search, user), pageable)
                .map(seriesMapper::toResponse);
    }

    // ════════════════════════════════════════════════════════════
    // 2. GET BY ID — Chi tiết 1 series
    // ════════════════════════════════════════════════════════════

    /**
     * Lấy chi tiết một series theo ID.
     *
     * 📌 Ai cũng có thể xem chi tiết series (không cần @PreAuthorize).
     *    Nếu cần role-based, thêm kiểm tra ở đây hoặc Controller.
     *
     * @param id ID của series cần tìm
     * @return SeriesResponse thông tin chi tiết
     * @throws AppException 404 — nếu không tìm thấy series
     */
    @Transactional(readOnly = true)
    public SeriesResponse getById(Long id) {
        // findById trả về Optional — dùng orElseThrow để ném 404
        Series series = seriesRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Series not found"));
        // MapStruct: Series → SeriesResponse
        return seriesMapper.toResponse(series);
    }

    // ════════════════════════════════════════════════════════════
    // 3. CREATE — Tạo series mới
    // ════════════════════════════════════════════════════════════

    /**
     * Tạo một series mới.
     *
     * 📌 @PreAuthorize("hasAuthority('MANGAKA')") ở Controller:
     *    Chỉ MANGAKA mới được gọi endpoint này.
     *
     * 📌 Luồng xử lý:
     *    1. Lấy User entity từ DB — nếu user không tồn tại → 401.
     *    2. MapStruct toEntity(): SeriesRequest → Series entity
     *       - status mặc định = DRAFT (cấu hình trong @Mapping)
     *       - isMature null-safe: null → false
     *       - mangaka = user từ DB
     *    3. seriesRepository.save(): insert vào database
     *    4. MapStruct toResponse(): Series entity → SeriesResponse DTO
     *
     * @param request thông tin series từ client (title bắt buộc)
     * @param user    user đang đăng nhập (lấy userId để gán mangaka)
     * @return SeriesResponse — series vừa tạo (kèm id từ database)
     * @throws AppException 401 — nếu user không tồn tại
     */
    @Transactional
    public SeriesResponse create(SeriesRequest request, CustomUserDetails user) {
        // Bước 1: Xác thực user còn tồn tại trong DB
        User mangaka = userRepository.findById(user.getUserId())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

        // Bước 2: MapStruct chuyển Request → Entity (status DRAFT, mangaka gán tự động)
        // Bước 3: Lưu xuống DB
        // Bước 4: Map Entity → DTO và trả về
        return seriesMapper.toResponse(
                seriesRepository.save(seriesMapper.toEntity(request, mangaka)));
    }

    // ════════════════════════════════════════════════════════════
    // 4. UPDATE — Cập nhật thông tin series
    // ════════════════════════════════════════════════════════════

    /**
     * Cập nhật thông tin series.
     *
     * 📌 @PreAuthorize("hasAuthority('MANGAKA')") ở Controller:
     *    Chỉ MANGAKA mới được gọi.
     *
     * 📌 Ownership check:
     *    Dùng findByIdAndMangakaId() — nếu không tìm thấy series
     *    với id và mangakaId tương ứng → không phải chủ sở hữu → 404.
     *
     * 📌 Null-safe update với MapStruct:
     *    updateEntity() dùng @BeanMapping(nullValuePropertyMappingStrategy = IGNORE)
     *    → Chỉ update field khác null trong request.
     *    → Field null trong request giữ nguyên giá trị cũ.
     *    → Logic này giống hệt đoạn if (title != null) series.setTitle() thủ công.
     *
     * 📌 Các field KHÔNG được update qua endpoint này:
     *    - status:  dùng submit/approve/reject/updateStatus riêng
     *    - mangaka: không thể đổi chủ sở hữu
     *    - tantouEditor: do EDITORIAL_BOARD gán khi approve
     *    - chapterCount/currentRank/currentTier: denormalized, module khác quản lý
     *
     * @param id      ID của series cần sửa
     * @param request dữ liệu mới (chỉ gửi field muốn thay đổi, null = giữ nguyên)
     * @param user    user đang đăng nhập (để kiểm tra ownership)
     * @return SeriesResponse — series sau khi cập nhật
     * @throws AppException 404 — nếu không tìm thấy hoặc không phải chủ
     */
    @Transactional
    public SeriesResponse update(Long id, SeriesRequest request, CustomUserDetails user) {
        // Kiểm tra ownership: series này có thuộc về user không?
        Series series = seriesRepository.findByIdAndMangakaId(id, user.getUserId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Series not found or not owned by you"));

        // MapStruct null-safe: chỉ update field khác null
        seriesMapper.updateEntity(series, request);

        // Lưu thay đổi → map sang DTO → trả về
        return seriesMapper.toResponse(seriesRepository.save(series));
    }

    // ════════════════════════════════════════════════════════════
    // 5. DELETE — Xoá series
    // ════════════════════════════════════════════════════════════

    /**
     * Xoá một series.
     *
     * 📌 @PreAuthorize("hasAuthority('MANGAKA')") ở Controller:
     *    Chỉ MANGAKA mới được gọi.
     *
     * 📌 Ownership check:
     *    findByIdAndMangakaId() — chỉ chủ sở hữu mới xoá được.
     *
     * 📌 Chỉ xoá được DRAFT series:
     *    - DRAFT:  có thể xoá (chưa publish, chưa ai thấy).
     *    - PENDING_APPROVAL: không được xoá (đã gửi duyệt).
     *    - ONGOING/HIATUS/...: không được xoá (đã publish).
     *
     * 📌 Khi có module Chapter:
     *    Cần kiểm tra series có chapter không trước khi xoá.
     *    Nếu có → cần xoá chapters trước hoặc chặn không cho xoá.
     *
     * @param id   ID của series cần xoá
     * @param user user đang đăng nhập (kiểm tra ownership)
     * @throws AppException 404 — nếu không tìm thấy hoặc không phải chủ
     * @throws AppException 400 — nếu series không ở trạng thái DRAFT
     */
    @Transactional
    public void delete(Long id, CustomUserDetails user) {
        // Kiểm tra ownership
        Series series = seriesRepository.findByIdAndMangakaId(id, user.getUserId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Series not found or not owned by you"));

        // Chỉ cho phép xoá series đang ở trạng thái DRAFT
        if (series.getStatus() != SeriesStatus.DRAFT) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Only DRAFT series can be deleted");
        }

        // Thực hiện xoá
        seriesRepository.delete(series);
    }
}
