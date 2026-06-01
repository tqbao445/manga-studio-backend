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
import com.mangaflow.studio.service.storage.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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

    /**
     * cloudinaryService: Service upload/xoá ảnh lên Cloudinary.
     * Dùng để upload ảnh bìa (cover) khi tạo/cập nhật series,
     * và xoá ảnh bìa khỏi Cloudinary khi xoá series.
     */
    private final CloudinaryService cloudinaryService;

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
    // 3. CREATE — Tạo series mới (kèm upload ảnh bìa)
    // ════════════════════════════════════════════════════════════

    /**
     * Tạo một series mới + upload ảnh bìa lên Cloudinary.
     *
     * 📌 Luồng xử lý:
     *    1. Lấy User entity từ DB — nếu user không tồn tại → 401.
     *    2. MapStruct toEntity(): SeriesRequest → Series entity
     *       - status mặc định = DRAFT
     *       - coverImageUrl để null (chưa có ảnh)
     *    3. seriesRepository.save(): insert vào database → series có ID
     *    4. Upload file lên Cloudinary:
     *       - folder: manga_studio/u{userId}/s{seriesId}/cover
     *       - trả về URL
     *    5. Gán URL vào series.coverImageUrl
     *       (JPA managed entity → tự động UPDATE khi commit transaction)
     *    6. MapStruct toResponse(): Series entity → SeriesResponse DTO
     *
     * 📌 Tại sao save rồi mới upload?
     *    Vì cần seriesId để tạo folder trên Cloudinary.
     *    JPA sinh ID sau khi save (IDENTITY strategy).
     *
     * @param request thông tin series từ client (title bắt buộc)
     * @param file    file ảnh bìa (multipart) — bắt buộc
     * @param user    user đang đăng nhập (lấy userId để gán mangaka)
     * @return SeriesResponse — series vừa tạo (kèm ảnh bìa)
     * @throws AppException 401 — nếu user không tồn tại
     */
    @Transactional
    public SeriesResponse create(SeriesRequest request, MultipartFile file, CustomUserDetails user) {
        // Bước 1: Xác thực user còn tồn tại trong DB
        User mangaka = userRepository.findById(user.getUserId())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

        // Bước 2: MapStruct chuyển Request → Entity (status DRAFT, mangaka gán tự động)
        Series series = seriesMapper.toEntity(request, mangaka);

        // Bước 3: Lưu xuống DB → lúc này series có ID (IDENTITY auto-increment)
        series = seriesRepository.save(series);

        // Bước 4: Upload ảnh bìa lên Cloudinary
        //   folder = manga_studio/u{userId}/s{seriesId}/cover
        //   public_id = "cover" (overwrite=true → ghi đè nếu re-upload)
        String coverUrl = cloudinaryService.uploadCover(file, mangaka.getId(), series.getId());

        // Bước 5: Gán URL ảnh bìa vào entity
        //   JPA biết entity này đã được save (managed) → tự động UPDATE
        //   mà không cần gọi seriesRepository.save() lần nữa
        series.setCoverImageUrl(coverUrl);

        // Bước 6: Map Entity → DTO và trả về
        return seriesMapper.toResponse(series);
    }

    // ════════════════════════════════════════════════════════════
    // 4. UPDATE — Cập nhật thông tin series (có thể kèm ảnh bìa mới)
    // ════════════════════════════════════════════════════════════

    /**
     * Cập nhật thông tin series. Hỗ trợ 3 trường hợp xử lý ảnh bìa:
     *
     * ┌──────────────────────┬───────────────────┬──────────────────────────────┐
     * │ Trường hợp           │ Client gửi        │ Backend xử lý                │
     * ├──────────────────────┼───────────────────┼──────────────────────────────┤
     * │ 1. Không đổi ảnh     │ Không gửi file    │ Null-safe update JSON        │
     * │                      │                   │ Giữ nguyên ảnh cũ            │
     * ├──────────────────────┼───────────────────┼──────────────────────────────┤
     * │ 2. Đổi ảnh mới       │ Có gửi file       │ Xoá ảnh cũ trên Cloudinary   │
     * │                      │                   │ Upload ảnh mới               │
     * │                      │                   │ Gán URL mới vào entity       │
     * ├──────────────────────┼───────────────────┼──────────────────────────────┤
     * │ 3. Xoá ảnh           │ coverImageUrl = ""│ Xoá ảnh trên Cloudinary      │
     * │                      │ Không gửi file    │ Gán coverImageUrl = null     │
     * └──────────────────────┴───────────────────┴──────────────────────────────┘
     *
     * 📌 Ownership check:
     *    Dùng findByIdAndMangakaId() — nếu không tìm thấy series
     *    với id và mangakaId tương ứng → không phải chủ sở hữu → 404.
     *
     * 📌 Null-safe update với MapStruct:
     *    updateEntity() dùng @BeanMapping(nullValuePropertyMappingStrategy = IGNORE)
     *    → Chỉ update field khác null trong request.
     *    → Field null trong request giữ nguyên giá trị cũ.
     *    → coverImageUrl trong JSON: null → giữ nguyên, "" → xoá ảnh.
     *
     * 📌 Các field KHÔNG được update qua endpoint này:
     *    - status:  dùng submit/approve/reject/updateStatus riêng
     *    - mangaka: không thể đổi chủ sở hữu
     *    - tantouEditor: do EDITORIAL_BOARD gán khi approve
     *    - chapterCount/currentRank/currentTier: denormalized, module khác quản lý
     *
     * @param id      ID của series cần sửa
     * @param request dữ liệu mới (chỉ gửi field muốn thay đổi, null = giữ nguyên)
     * @param file    file ảnh bìa mới (optional) — null nếu không muốn đổi ảnh
     * @param user    user đang đăng nhập (để kiểm tra ownership)
     * @return SeriesResponse — series sau khi cập nhật
     * @throws AppException 404 — nếu không tìm thấy hoặc không phải chủ
     */
    @Transactional
    public SeriesResponse update(Long id, SeriesRequest request, MultipartFile file, CustomUserDetails user) {
        // Bước 1: Kiểm tra ownership
        Series series = seriesRepository.findByIdAndMangakaId(id, user.getUserId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Series not found or not owned by you"));

        // Bước 2: MapStruct null-safe update các field JSON khác (title, genre, ...)
        // Làm trước để tránh request.coverImageUrl ghi đè URL mới từ Cloudinary
        seriesMapper.updateEntity(series, request);

        // Bước 3: Xử lý ảnh bìa (nếu có) — ưu tiên ghi đè coverImageUrl sau cùng
        if (file != null && !file.isEmpty()) {
            // ── Trường hợp 2: Có file → đổi ảnh bìa mới ──
            // Xoá ảnh cũ trên Cloudinary nếu có
            if (series.getCoverImageUrl() != null) {
                cloudinaryService.deleteImageByUrl(series.getCoverImageUrl());
            }
            // Upload ảnh mới
            String coverUrl = cloudinaryService.uploadCover(file, series.getMangaka().getId(), series.getId());
            series.setCoverImageUrl(coverUrl);
        } else if (request.getCoverImageUrl() != null && request.getCoverImageUrl().isEmpty()) {
            // ── Trường hợp 3: coverImageUrl = "" → xoá ảnh ──
            if (series.getCoverImageUrl() != null) {
                cloudinaryService.deleteImageByUrl(series.getCoverImageUrl());
            }
            series.setCoverImageUrl(null);
        }
        // ── Trường hợp 1: không có file, coverImageUrl null → giữ nguyên ──

        // Bước 4: Save → map sang DTO → trả về
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

        // Xoá ảnh bìa trên Cloudinary trước (tránh rác)
        if (series.getCoverImageUrl() != null) {
            cloudinaryService.deleteImageByUrl(series.getCoverImageUrl());
        }

        // Thực hiện xoá
        seriesRepository.delete(series);
    }
}
