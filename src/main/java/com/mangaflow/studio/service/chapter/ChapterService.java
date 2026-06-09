package com.mangaflow.studio.service.chapter;

import com.mangaflow.studio.common.exception.AppException;
import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.chapter.mapper.ChapterMapper;
import com.mangaflow.studio.dto.chapter.request.ChapterRequest;
import com.mangaflow.studio.dto.chapter.response.ChapterResponse;
import com.mangaflow.studio.model.chapter.Chapter;
import com.mangaflow.studio.model.chapter.ChapterStatus;
import com.mangaflow.studio.model.series.Series;
import com.mangaflow.studio.model.page.PageStatus;
import com.mangaflow.studio.repository.chapter.ChapterRepository;
import com.mangaflow.studio.repository.page.PageRepository;
import com.mangaflow.studio.repository.series.SeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ── ChapterService ──
 * Service xử lý toàn bộ logic nghiệp vụ liên quan đến Chapter.
 * Là tầng trung gian giữa Controller (API) và Repository (DB).
 * <p>
 * 📌 @Service: Spring Bean — chứa business logic, quản lý transaction.
 * 📌 @RequiredArgsConstructor: Lombok — DI constructor cho tất cả field final.
 * <p>
 * ══════════════════════════════════════════════════════════════════
 *  Scope của Service này (hiện tại):
 * ══════════════════════════════════════════════════════════════════
 *  ✅ getSeriesIdByChapterId()   — Query: lấy seriesId từ chapterId (cho PageController)
 *  ✅ getChaptersBySeries()      — Query: danh sách chapters của series
 *  ✅ create()                   — Command: tạo chapter mới
 * <p>
 *  ✅ update()                   — Command: cập nhật chapter (null-safe)
 *  ✅ delete()                   — Command: xoá chapter (chỉ DRAFT)
 * <p>
 *  ✅ getById()                   — Query: chi tiết 1 chapter
 * <p>
 *  ❌ Các chức năng sẽ phát triển sau:
 *     - submit/approve/... — Workflow chuyển trạng thái
 * <p>
 * 📌 ChapterMapper (MapStruct):
 *    - toEntity():       ChapterRequest → Chapter entity (create)
 *    - toResponse():     Chapter entity → ChapterResponse DTO
 *    - updateEntity():   ChapterRequest → merge Chapter entity (update, null-safe)
 */
@Service
@RequiredArgsConstructor
public class ChapterService {

    // ────────────────────────────────────────────────────────────
    // Repositories & Dependencies
    // ────────────────────────────────────────────────────────────

    /**
     * chapterRepository: Repository chính — thao tác với bảng chapter.
     * Cung cấp CRUD cơ bản + các method query tuỳ chỉnh:
     * - existsBySeriesIdAndChapterNumber(): kiểm tra trùng số chapter
     * - countBySeriesId(): đếm số chapters trong series (cập nhật denormalized)
     * - findByIdAndSeries_MangakaId(): kiểm tra ownership (dùng sau cho update/delete)
     */
    private final ChapterRepository chapterRepository;

    /**
     * seriesRepository: Repository của Series.
     * Dùng để:
     * - Tìm series theo id và mangakaId (kiểm tra ownership khi tạo chapter)
     * - Cập nhật series.chapterCount (denormalized field) sau khi tạo/xoá chapter
     */
    private final SeriesRepository seriesRepository;

    /**
     * pageRepository: Dùng để đếm pages cho progress calculation.
     */
    private final PageRepository pageRepository;

    /**
     * chapterMapper: MapStruct — chuyển đổi giữa Entity và DTO.
     * - toEntity():   ChapterRequest + Series → Chapter (create, status=DRAFT)
     * - toResponse(): Chapter → ChapterResponse (trả về frontend)
     * - updateEntity(): ChapterRequest → merge Chapter (update, null-safe — dùng sau)
     */
    private final ChapterMapper chapterMapper;

    // ════════════════════════════════════════════════════════════════
    // HELPER: GET SERIES ID FROM CHAPTER ID
    // ════════════════════════════════════════════════════════════════

    /**
     * Lấy seriesId từ chapterId.
     * <p>
     * 📌 Mục đích:
     * Được PageController gọi khi upload page.
     * Trước đây hardcode return 1L — giờ dùng ChapterService để truy vấn thật.
     * <p>
     * 📌 Hiệu năng:
     * Chapter.getSeries() là LAZY proxy.
     * Gọi .getId() trên proxy KHÔNG gây thêm truy vấn DB
     * vì ID đã được lưu sẵn trong proxy object (từ FK column).
     * <p>
     * 📌 @Transactional(readOnly = true):
     * - Không cần dirty checking → Hibernate bỏ qua cơ chế flush.
     * - Tối ưu hiệu năng — không cần transaction write lock.
     *
     * @param chapterId ID của chapter cần tra cứu
     * @return ID của series chứa chapter đó
     * @throws AppException 404 — nếu không tìm thấy chapter
     */
    @Transactional(readOnly = true)
    public Long getSeriesIdByChapterId(Long chapterId) {
        // ── Bước 1: Tìm chapter theo id ──
        // findById trả về Optional — dùng orElseThrow để ném 404
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Chapter not found"));

        // ── Bước 2: Lấy seriesId từ LAZY proxy ──
        // chapter.getSeries() trả về proxy (không query DB)
        // .getId() chỉ đọc FK đã có sẵn → 0 query
        return chapter.getSeries().getId();
    }

    // ════════════════════════════════════════════════════════════════
    // 1. GET CHAPTERS BY SERIES — Danh sách chapters của series
    // ════════════════════════════════════════════════════════════════

    /**
     * Lấy danh sách chapters của một series, sắp xếp theo chapterNumber tăng dần.
     * <p>
     * 📌 @Transactional(readOnly = true):
     * - Không cần dirty checking → Hibernate bỏ qua cơ chế flush.
     * - Tối ưu hiệu năng — không cần transaction write lock.
     * <p>
     * 📌 chapterRepository.findBySeriesIdOrderByChapterNumberAsc():
     * - Query: SELECT * FROM chapter WHERE series_id = ? ORDER BY chapter_number ASC
     * - Trả về List<Chapter> đã sắp xếp.
     * <p>
     * 📌 chapterMapper::toResponse:
     * - Method reference: map từng Chapter entity → ChapterResponse DTO.
     * - Chuyển đổi tự động nhờ MapStruct.
     *
     * @param seriesId ID của series cần lấy chapters
     * @return List<ChapterResponse> danh sách chapters
     * @throws AppException 404 — nếu series không tồn tại
     */
    @Transactional(readOnly = true)
    public List<ChapterResponse> getChaptersBySeries(Long seriesId) {
        seriesRepository.findById(seriesId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Series not found"));

        return chapterRepository.findBySeriesIdOrderByChapterNumberAsc(seriesId)
                .stream()
                .map(chapterMapper::toResponse)
                .toList();
    }

    // ════════════════════════════════════════════════════════════════
    // 1B. GET BY ID — Chi tiết chapter
    // ════════════════════════════════════════════════════════════════

    /**
     * Lấy thông tin chi tiết của một chapter theo ID.
     * <p>
     * 📌 @PreAuthorize("isAuthenticated()") ở Controller:
     * Tất cả user đã đăng nhập đều xem được.
     * <p>
     * 📌 Luồng xử lý:
     * <p>
     * ┌──────────┐
     * │ Bước 1   │ Tìm chapter theo ID
     * │          │ chapterRepository.findById(id)
     * │          │ → Nếu không tìm thấy → throw 404
     * └────┬─────┘
     *      v
     * ┌──────────┐
     * │ Bước 2   │ Map entity → Response DTO
     * │          │ chapterMapper.toResponse(chapter)
     * │          │ MapStruct tự động mapping
     * └────┬─────┘
     *      v
     * ┌──────────┐
     * │ Kết quả  │ Trả về ChapterResponse
     * └──────────┘
     * <p>
     * 📌 chapterMapper.toResponse():
     * - Chuyển Chapter entity → ChapterResponse DTO
     * - MapStruct tự động ánh xạ các field cùng tên
     *
     * @param id ID của chapter cần lấy
     * @return ChapterResponse chi tiết chapter
     * @throws AppException 404 — không tìm thấy chapter
     */
    @Transactional(readOnly = true)
    public ChapterResponse getById(Long id) {
        return chapterMapper.toResponse(
                chapterRepository.findById(id)
                        .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Chapter not found"))
        );
    }

    // ════════════════════════════════════════════════════════════════
    // 2. CREATE — Tạo chapter mới
    // ════════════════════════════════════════════════════════════════

    /**
     * Tạo một chapter mới trong series.
     * <p>
     * 📌 @PreAuthorize("hasRole('MANGAKA')") ở Controller:
     * Chỉ MANGAKA mới được gọi endpoint này.
     * <p>
     * 📌 Luồng xử lý (5 bước):
     * <p>
     * ┌──────────┐
     * │ Bước 1   │ Ownership check
     * │          │ seriesRepository.findByIdAndMangakaId()
     * │          │ → Kiểm tra series tồn tại và user là chủ sở hữu
     * │          │ → Nếu không → throw 404
     * └────┬─────┘
     *      ▼
     * ┌──────────┐
     * │ Bước 2   │ Uniqueness check
     * │          │ chapterRepository.existsBySeriesIdAndChapterNumber()
     * │          │ → Kiểm tra chapterNumber đã tồn tại trong series chưa
     * │          │ → Nếu có → throw 400
     * └────┬─────┘
     *      ▼
     * ┌──────────┐
     * │ Bước 3   │ MapStruct: Request → Entity
     * │          │ chapterMapper.toEntity(request, series)
     * │          │ → status = DRAFT (constant)
     * │          │ → pageCount = 0, progressPercent = 0
     * │          │ → series = series entity từ bước 1
     * └────┬─────┘
     *      ▼
     * ┌──────────┐
     * │ Bước 4   │ Save chapter
     * │          │ chapterRepository.save(chapter)
     * │          │ → INSERT INTO chapter (...) VALUES (...)
     * │          │ → JPA trả về entity với id đã sinh
     * └────┬─────┘
     *      ▼
     * ┌──────────┐
     * │ Bước 5   │ Update denormalized field
     * │          │ series.setChapterCount(count)
     * │          │ seriesRepository.save(series)
     * │          │ → UPDATE series SET chapter_count = ? WHERE id = ?
     * └────┬─────┘
     *      ▼
     * ┌──────────┐
     * │ Bước 6   │ MapStruct: Entity → Response DTO
     * │          │ chapterMapper.toResponse(savedChapter)
     * │          │ → Trả về ChapterResponse cho Controller
     * └──────────┘
     * <p>
     * 📌 @Transactional:
     * Toàn bộ 5 bước chạy trong 1 transaction.
     * Nếu bất kỳ bước nào fail → tất cả rollback.
     *
     * @param seriesId ID của series muốn thêm chapter
     * @param request  DTO từ client: chapterNumber, title, deadline
     * @param user     User đang đăng nhập (lấy userId để kiểm tra ownership)
     * @return ChapterResponse — chapter vừa tạo (kèm id từ database)
     * @throws AppException 404 — nếu không tìm thấy series hoặc không phải chủ
     * @throws AppException 400 — nếu chapterNumber đã tồn tại
     */
    @Transactional
    public ChapterResponse create(Long seriesId, ChapterRequest request, CustomUserDetails user) {
        // ── Bước 1: Ownership check ──
        // findByIdAndMangakaId() kiểm tra đồng thời:
        //   1. Series có tồn tại không?
        //   2. User hiện tại có phải mangaka của series không?
        // Nếu không thoả mãn → throw 404 (không tiết lộ reason cụ thể)
        Series series = seriesRepository.findByIdAndMangakaId(seriesId, user.getUserId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Series not found or not owned by you"));

        // ── Bước 2: Uniqueness check ──
        // Đảm bảo không có 2 chapter trùng số trong cùng 1 series
        // existsBy...() → SELECT EXISTS(SELECT 1 FROM chapter WHERE ...)
        // → Nhanh hơn findAll() vì chỉ trả về true/false
        if (chapterRepository.existsBySeriesIdAndChapterNumber(seriesId, request.getChapterNumber())) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Chapter number " + request.getChapterNumber() + " already exists in this series");
        }

        // ── Bước 3: MapStruct chuyển Request → Entity ──
        // toEntity(request, series):
        //   - Map chapterNumber, title, deadline từ request
        //   - Gán series = entity từ DB
        //   - status = "DRAFT" (constant)
        //   - pageCount = 0, progressPercent = 0 (constant)
        //   - createdAt / updatedAt = null → @PrePersist tự xử lý
        Chapter chapter = chapterMapper.toEntity(request, series);

        // ── Bước 4: Lưu chapter xuống database ──
        // chapterRepository.save(chapter):
        //   → INSERT INTO chapter (series_id, chapter_number, title,
        //                          page_count, progress_percent, deadline,
        //                          status, created_at, updated_at)
        //     VALUES (?, ?, ?, 0, 0, ?, 'DRAFT', NOW(), NOW())
        //   → SQL Server tự sinh id nhờ IDENTITY(1,1)
        //   → JPA trả về entity với id đã được gán
        Chapter savedChapter = chapterRepository.save(chapter);

        // ── Bước 5: Cập nhật denormalized field chapterCount ──
        // Lý do denormalized:
        //   - Tránh phải JOIN bảng chapter mỗi lần hiển thị danh sách series
        //   - chapter_count là field cache trong bảng series
        //   - Cập nhật ngay sau khi tạo/xoá chapter để đảm bảo consistency
        int totalChapters = (int) chapterRepository.countBySeriesId(seriesId);
        series.setChapterCount(totalChapters);
        seriesRepository.save(series);  // UPDATE series SET chapter_count = ? WHERE id = ?

        // ── Bước 6: MapStruct Entity → Response DTO ──
        // toResponse(savedChapter):
        //   - seriesId = series.id
        //   - seriesTitle = series.title
        //   - Các field còn lại map trực tiếp
        // → Trả về DTO cho Controller → JSON cho Frontend
        return chapterMapper.toResponse(savedChapter);
    }

    // ════════════════════════════════════════════════════════════════
    // 3. UPDATE — Cập nhật thông tin chapter
    // ════════════════════════════════════════════════════════════════

    /**
     * Cập nhật thông tin chapter.
     * <p>
     * 📌 @PreAuthorize("hasRole('MANGAKA')") ở Controller:
     * Chỉ MANGAKA mới được gọi.
     * <p>
     * 📌 Ownership check:
     * Dùng findByIdAndSeries_MangakaId() — nếu không tìm thấy chapter
     * với id và mangakaId tương ứng → không phải chủ sở hữu → 404.
     * <p>
     * 📌 Null-safe update với MapStruct:
     * updateEntity() dùng @BeanMapping(nullValuePropertyMappingStrategy = IGNORE)
     * → Chỉ update field khác null trong request.
     * → Field null trong request giữ nguyên giá trị cũ.
     * <p>
     * 📌 Các field KHÔNG được update qua endpoint này:
     * - id: không đổi khoá chính
     * - series: không thể đổi series
     * - status: dùng workflow riêng (submit/approve/...)
     * - pageCount, progressPercent: module Page quản lý
     * - publishDate: do Editorial Board set khi publish
     * - createdAt, updatedAt: lifecycle callback tự động
     *
     * @param id      ID của chapter cần sửa
     * @param request dữ liệu mới (chỉ gửi field muốn thay đổi, null = giữ nguyên)
     * @param user    user đang đăng nhập (để kiểm tra ownership)
     * @return ChapterResponse — chapter sau khi cập nhật
     * @throws AppException 404 — nếu không tìm thấy hoặc không phải chủ
     */
    @Transactional
    public ChapterResponse update(Long id, ChapterRequest request, CustomUserDetails user) {
        // ── Bước 1: Ownership check ──
        // findByIdAndSeries_MangakaId() kiểm tra đồng thời:
        //   1. Chapter có tồn tại không?
        //   2. Series của chapter này có thuộc về user hiện tại không?
        Chapter chapter = chapterRepository.findByIdAndSeries_MangakaId(id, user.getUserId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Chapter not found or not owned by you"));

        // ── Bước 2: MapStruct null-safe update ──
        // updateEntity() chỉ set field KHÁC NULL trong request
        // Field null → bỏ qua, giữ nguyên giá trị cũ
        // → Không cần viết: if (title != null) chapter.setTitle(title)
        chapterMapper.updateEntity(chapter, request);

        // ── Bước 3: Lưu thay đổi ──
        // JPA detect changes tự động nhờ @Transactional,
        // nhưng gọi save() để chắc chắn flush xuống DB
        Chapter savedChapter = chapterRepository.save(chapter);

        // ── Bước 4: MapStruct Entity → Response DTO ──
        return chapterMapper.toResponse(savedChapter);
    }

    // ════════════════════════════════════════════════════════════════
    // 4. DELETE — Xoá chapter
    // ════════════════════════════════════════════════════════════════

    /**
     * Xoá một chapter.
     * <p>
     * 📌 @PreAuthorize("hasRole('MANGAKA')") ở Controller:
     * Chỉ MANGAKA mới được gọi.
     * <p>
     * 📌 Ownership check:
     * findByIdAndSeries_MangakaId() — chỉ chủ sở hữu mới xoá được.
     * <p>
     * 📌 Chỉ xoá được DRAFT chapter:
     * - DRAFT: có thể xoá (chưa publish, chưa ai thấy).
     * - Các status khác: không được xoá (đã có tiến độ).
     * <p>
     * 📌 Sau khi xoá:
     * Cập nhật lại denormalized field chapterCount trong series.
     *
     * @param id   ID của chapter cần xoá
     * @param user user đang đăng nhập (kiểm tra ownership)
     * @throws AppException 404 — nếu không tìm thấy hoặc không phải chủ
     * @throws AppException 400 — nếu chapter không ở trạng thái DRAFT
     */
    @Transactional
    public void delete(Long id, CustomUserDetails user) {
        // ── Bước 1: Ownership check ──
        // Kiểm tra chapter tồn tại và user là chủ sở hữu series
        Chapter chapter = chapterRepository.findByIdAndSeries_MangakaId(id, user.getUserId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Chapter not found or not owned by you"));

        // ── Bước 2: Status check ──
        // Chỉ cho phép xoá chapter đang ở trạng thái DRAFT
        if (chapter.getStatus() != ChapterStatus.DRAFT) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Only DRAFT chapters can be deleted");
        }

        Long seriesId = chapter.getSeries().getId();

        // ── Bước 3: Xoá chapter ──
        // Cascade sẽ tự động xoá pages nếu có (ON DELETE CASCADE trong DB)
        chapterRepository.delete(chapter);

        // ── Bước 4: Cập nhật denormalized field chapterCount ──
        Series series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Series not found"));
        series.setChapterCount((int) chapterRepository.countBySeriesId(seriesId));
        seriesRepository.save(series);
    }

    // ════════════════════════════════════════════════════════════════
    //  PROGRESS — Tính toán tiến độ chapter
    // ════════════════════════════════════════════════════════════════

    /**
     * Tính toán lại progressPercent và pageCount của chapter
     * dựa trên số page COMPLETED / tổng số pages.
     * <p>
     * Được gọi khi:
     * - Upload page mới
     * - Xoá page
     * - Mangaka đánh dấu page là COMPLETED
     *
     * @param chapterId ID của chapter cần tính
     */
    @Transactional
    public void recalculateProgress(Long chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Chapter not found"));

        long totalPages = pageRepository.countByChapterId(chapterId);
        long completedPages = pageRepository.countByChapterIdAndStatus(chapterId, PageStatus.COMPLETED);

        chapter.setPageCount((int) totalPages);
        chapter.setProgressPercent(totalPages == 0 ? 0 : (int) (completedPages * 100 / totalPages));

        chapterRepository.save(chapter);
    }
}
