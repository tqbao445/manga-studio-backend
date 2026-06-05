package com.mangaflow.studio.service.page;

import com.mangaflow.studio.common.exception.AppException;
import com.mangaflow.studio.dto.page.mapper.PageMapper;
import com.mangaflow.studio.dto.page.response.PageResponse;
import com.mangaflow.studio.model.page.Layer;
import com.mangaflow.studio.model.page.Page;
import com.mangaflow.studio.model.page.PageStatus;
import com.mangaflow.studio.repository.page.LayerRepository;
import com.mangaflow.studio.repository.page.PageRepository;
import com.mangaflow.studio.service.page.LayerService;
import com.mangaflow.studio.service.storage.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ── PageService ──
 * Service xử lý toàn bộ logic nghiệp vụ liên quan đến Page.
 * Là tầng trung gian giữa Controller (API) và Repository (DB) + CloudinaryService (storage).
 *
 * 📌 @Service: Spring Bean — chứa business logic, quản lý transaction.
 * 📌 @RequiredArgsConstructor: Lombok — DI constructor cho tất cả field final.
 * 📌 @Transactional: Tất cả method trong class đều chạy trong transaction.
 *    (Đọc: readOnly=true, Ghi: mặc định readOnly=false)
 *
 * ══════════════════════════════════════════════════════════════════
 *  Luồng xử lý 1 request:
 * ══════════════════════════════════════════════════════════════════
 *  Frontend request
 *       │
 *       ▼
 *  PageController (nhận HTTP request, validate)
 *       │
 *       ▼
 *  PageService (xử lý logic nghiệp vụ) ← BẠN ĐANG Ở ĐÂY
 *       │
 *       ├── CloudinaryService (upload/xoá ảnh)
 *       │
 *       ▼
 *  PageRepository (lưu/truy vấn database)
 *       │
 *       ▼
 *  Response về frontend
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PageService {

    /**
     * pageRepository: Repository chính — thao tác với bảng pages.
     * Cung cấp CRUD + các method query tuỳ chỉnh.
     */
    private final PageRepository pageRepository;

    /**
     * cloudinaryService: Service upload/xoá ảnh lên Cloudinary.
     * Dùng khi tạo page mới (upload) và xoá page (delete).
     */
    private final CloudinaryService cloudinaryService;

    /**
     * pageMapper: MapStruct — chuyển đổi Page entity → PageResponse DTO.
     */
    private final PageMapper pageMapper;

    /**
     * layerRepository: Repository để truy vấn layers của page.
     * Dùng trong mergeLayers() — lấy danh sách layers cần composite.
     */
    private final LayerRepository layerRepository;

    /**
     * layerService: Service xử lý Layer CRUD.
     * Dùng trong flattenPage() — xoá tất cả layers của page (DB + Cloudinary).
     */
    private final LayerService layerService;

    // ════════════════════════════════════════════════════════════════
    // 1. GET PAGES BY CHAPTER — Lấy danh sách pages
    // ════════════════════════════════════════════════════════════════

    /**
     * Lấy danh sách pages của 1 chapter, sắp xếp theo pageNumber tăng dần.
     *
     * 📌 @Transactional(readOnly = true):
     *    - Không cần dirty checking → Hibernate bỏ qua cơ chế flush.
     *    - Tối ưu hiệu năng — không cần transaction write lock.
     *
     * 📌 pageRepository.findByChapterIdOrderByPageNumberAsc():
     *    - Query: SELECT * FROM pages WHERE chapter_id = ? ORDER BY page_number ASC
     *    - Trả về List<Page> đã sắp xếp.
     *
     * 📌 pageMapper::toResponse:
     *    - Method reference: map từng Page entity → PageResponse DTO.
     *    - Chuyển đổi tự động nhờ MapStruct.
     *
     * @param chapterId ID của chapter
     * @return List<PageResponse> danh sách pages
     */
    @Transactional(readOnly = true)
    public List<PageResponse> getPagesByChapter(Long chapterId) {
        // Query database → lấy list pages đã sắp xếp theo pageNumber
        return pageRepository.findByChapterIdOrderByPageNumberAsc(chapterId)
                .stream()                    // Chuyển List<Page> thành Stream<Page>
                .map(pageMapper::toResponse) // Map từng Page → PageResponse
                .toList();                   // Gom lại thành List<PageResponse>
    }

    // ════════════════════════════════════════════════════════════════
    // 2. UPLOAD SINGLE PAGE — Upload 1 page
    // ════════════════════════════════════════════════════════════════

    /**
     * Upload 1 file ảnh lên Cloudinary và tạo Page entity trong database.
     *
     * 📌 Quy trình:
     *    1. Kiểm tra pageNumber đã tồn tại trong chapter chưa
     *       - Nếu tồn tại → throw BAD_REQUEST (không cho ghi đè)
     *       - Nếu chưa → tiếp tục
     *    2. Upload file lên Cloudinary → nhận URLs + kích thước
     *    3. Tạo Page entity mới
     *    4. Lưu vào database
     *    5. Map sang DTO và trả về
     *
     * @param chapterId  ID của chapter
     * @param file       File ảnh từ frontend
     * @param pageNumber Số thứ tự page trong chapter
     * @return PageResponse page vừa tạo
     * @throws AppException 400 — nếu pageNumber đã tồn tại
     */
    public PageResponse uploadPage(Long chapterId,
                                   MultipartFile file, Integer pageNumber) {
        // ── Bước 1: Kiểm tra trùng pageNumber ──
        if (pageRepository.existsByChapterIdAndPageNumber(chapterId, pageNumber)) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Page number " + pageNumber + " already exists in this chapter");
        }

        // ── Bước 2: Tạo Page entity trước (chưa có URLs) → có pageId ──
        Page page = Page.builder()
                .chapterId(chapterId)
                .pageNumber(pageNumber)
                .status(PageStatus.UPLOADED)
                .originalImageUrl("")
                .webImageUrl("")
                .publicId("pending")
                .width(0)
                .height(0)
                .build();
        Page savedPage = pageRepository.save(page);

        try {
            // ── Bước 3: Upload file lên Cloudinary với pageId ──
            CloudinaryService.UploadResult uploadResult = cloudinaryService.uploadPage(
                    file, chapterId, savedPage.getId());

            // ── Bước 4: Cập nhật page với URLs từ Cloudinary ──
            savedPage.setOriginalImageUrl(uploadResult.getOriginalImageUrl());
            savedPage.setWebImageUrl(uploadResult.getWebImageUrl());
            savedPage.setThumbnailUrl(uploadResult.getThumbnailUrl());
            savedPage.setPublicId(uploadResult.getPublicId());
            savedPage.setWidth(uploadResult.getWidth());
            savedPage.setHeight(uploadResult.getHeight());
            pageRepository.save(savedPage);

            return pageMapper.toResponse(savedPage);

        } catch (Exception e) {
            // Cloudinary lỗi → xoá page rác trong DB
            pageRepository.delete(savedPage);
            throw e;
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 3. UPLOAD BATCH PAGES — Import nhiều page 1 lúc (auto pageNumber)
    // ════════════════════════════════════════════════════════════════

    /**
     * Upload nhiều file ảnh cùng lúc. Backend tự động gán pageNumber
     * dựa trên số trang cao nhất hiện có trong chapter.
     *
     * 📌 Cách gán pageNumber:
     *    - Tìm max(pageNumber) trong chapter (nếu chưa có trang nào → lấy 0)
     *    - File đầu tiên trong mảng → max + 1
     *    - File thứ hai → max + 2
     *    - ...
     *
     *    VD: chapter đã có page 1,2,3
     *        → upload batch [fileA, fileB, fileC]
     *        → fileA = 4, fileB = 5, fileC = 6
     *
     * 📌 @Transactional:
     *    Toàn bộ batch chạy trong 1 transaction.
     *    Nếu 1 file upload lỗi → tất cả đều rollback.
     *
     * 📌 List<MultipartFile>:
     *    Danh sách files từ frontend. Frontend dùng <input type="file" multiple>
     *    để chọn nhiều file cùng lúc.
     *
     * @param chapterId ID của chapter
     * @param files     Mảng các file ảnh (không cần gửi pageNumbers)
     * @return List<PageResponse> danh sách pages vừa tạo (đã auto-assign pageNumber)
     */
    public List<PageResponse> uploadPagesBatch(Long chapterId,
                                                List<MultipartFile> files) {
        // ── Bước 1: Tính pageNumber bắt đầu ──
        List<Page> chapterPages = pageRepository.findByChapterIdOrderByPageNumberAsc(chapterId);

        int nextPageNumber;
        if (chapterPages.isEmpty()) {
            nextPageNumber = 1;
        } else {
            Page lastPage = chapterPages.get(chapterPages.size() - 1);
            nextPageNumber = lastPage.getPageNumber() + 1;
        }

        // ── Bước 2: Upload từng file với pageNumber tăng dần ──
        List<PageResponse> results = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            PageResponse response = uploadPage(
                    chapterId, files.get(i), nextPageNumber + i);
            results.add(response);
        }

        // ── Bước 3: Trả về danh sách pages vừa tạo ──
        return results;
    }

    // ════════════════════════════════════════════════════════════════
    // 4. DELETE PAGE — Xoá page
    // ════════════════════════════════════════════════════════════════

    /**
     * Xoá 1 page khỏi hệ thống.
     * Bao gồm xoá ảnh trên Cloudinary và xoá record trong database.
     *
     * 📌 Quy trình:
     *    1. Tìm page theo id
     *       - Nếu không tìm thấy → throw NOT_FOUND
     *    2. Xoá ảnh trên Cloudinary
     *    3. Xoá record trong database
     *
     * 📌 Tại sao không throw nếu xoá Cloudinary thất bại?
     *    Vì nếu Cloudinary lỗi (mạng, server error) mà vẫn throw thì
     *    page sẽ không xoá được. Thực tế, ưu tiên xoá DB trước hoặc
     *    có thể dùng cronjob dọn rác sau. Hiện tại để đơn giản,
     *    nếu Cloudinary lỗi → vẫn xoá DB và log lỗi.
     *
     * @param pageId ID của page cần xoá
     * @throws AppException 404 — nếu không tìm thấy page
     */
    public void deletePage(Long pageId) {
        // ── Bước 1: Tìm page ──
        // findById trả về Optional — dùng orElseThrow để ném 404
        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Page not found"));

        // ── Bước 2: Xoá ảnh trên Cloudinary ──
        // Dùng publicId để xoá (không cần version, format)
        // Nếu lỗi → throw RuntimeException (GlobalExceptionHandler xử lý)
        cloudinaryService.deleteImage(page.getPublicId());

        // ── Bước 3: Xoá layers trước (tránh FK constraint) ──
        layerService.deleteLayersByPage(pageId);

        // ── Bước 4: Xoá record trong database ──
        pageRepository.delete(page);
    }

    // ════════════════════════════════════════════════════════════════
    // 5. REORDER BATCH — Sắp xếp lại pages sau kéo thả
    // ════════════════════════════════════════════════════════════════

    /**
     * Sắp xếp lại toàn bộ pages trong chapter theo thứ tự mới.
     * Đây là endpoint chính cho tính năng "Kéo thả đổi vị trí page".
     *
     * 📌 Quy trình:
     *    1. Kiểm tra số lượng pageIds gửi lên có khớp với DB không
     *    2. Lấy tất cả pages trong chapter
     *    3. Gán pageNumber mới theo thứ tự mảng
     *    4. Lưu lại
     *
     * 📌 Frontend gửi:
     *    PUT /api/v1/chapters/{chapterId}/pages/reorder
     *    { "pageIds": [30, 10, 20] }
     *
     *    Nghĩa là: page 30 làm số 1, page 10 làm số 2, page 20 làm số 3
     *
     * 📌 @Transactional:
     *    Toàn bộ thao tác trong 1 transaction.
     *    Nếu lỗi giữa chừng → rollback, không lo bị cập nhật nửa vời.
     *
     * @param chapterId ID của chapter
     * @param pageIds   Danh sách page IDs theo thứ tự mới
     * @return List<PageResponse> danh sách pages sau khi sắp xếp
     * @throws AppException 400 — nếu số lượng pageIds không khớp
     */
    public List<PageResponse> reorderPages(Long chapterId, List<Long> pageIds) {
        // ── Bước 1: Kiểm tra số lượng ──
        // Đảm bảo frontend gửi đúng số pages trong chapter
        long totalPages = pageRepository.countByChapterId(chapterId);
        if (pageIds.size() != totalPages) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Invalid page count. Expected " + totalPages + " but got " + pageIds.size());
        }

        // ── Bước 2: Lấy tất cả pages trong chapter ──
        // findAllById() trả về List<Page> theo thứ tự id trong mảng...
        // Nhưng không đảm bảo thứ tự, nên cần tạo Map<id, Page> để tra cứu nhanh.
        // Đơn giản hơn: tạo Map từ id sang Page object.
        List<Page> allPages = pageRepository.findByChapterIdOrderByPageNumberAsc(chapterId);
        java.util.Map<Long, Page> pageMap = new java.util.HashMap<>();
        for (Page page : allPages) {
            pageMap.put(page.getId(), page);
        }

        // ── Bước 3: Pass 1 — đẩy tất cả pageNumber lên tạm ──
        // Tránh vi phạm UNIQUE(chapterId, pageNumber) khi Hibernate
        // UPDATE các page theo thứ tự không xác định.
        //
        // VD: chapter có [page1, page2, page3] → reorder thành [2, 3, 1]
        //     Nếu Hibernate update page2 lên 3 trước khi page3 được đổi
        //     → vi phạm unique (chapterId=1, pageNumber=3 tồn tại ở page3).
        //
        // Pass 1 gán tạm: page1.số1 → 10001, page2.số2 → 10002, page3.số3 → 10003
        // → flush để DB ghi nhận
        for (Page page : allPages) {
            page.setPageNumber(page.getPageNumber() + 10000);
        }
        pageRepository.saveAll(allPages);
        pageRepository.flush();

        // ── Bước 4: Pass 2 — gán pageNumber thật theo thứ tự mới ──
        // Lúc này DB đã không còn conflict, gán số thật an toàn.
        // VD: page3 → 1, page1 → 2, page2 → 3
        AtomicInteger index = new AtomicInteger(1);

        for (Long pageId : pageIds) {
            Page page = pageMap.get(pageId);
            if (page == null) {
                throw new AppException(HttpStatus.BAD_REQUEST,
                        "Page with id " + pageId + " not found in chapter " + chapterId);
            }
            page.setPageNumber(index.getAndIncrement());
        }

        // ── Bước 5: Lưu pageNumber thật ──
        pageRepository.saveAll(allPages);

        // ── Bước 6: Trả về danh sách đã sắp xếp ──
        return allPages.stream()
                .sorted((p1, p2) -> Integer.compare(p1.getPageNumber(), p2.getPageNumber()))
                .map(pageMapper::toResponse)
                .toList();
    }

    // ════════════════════════════════════════════════════════════════
    // 6. UPDATE ORDER 1 PAGE — Đổi số thứ tự 1 page
    // ════════════════════════════════════════════════════════════════

    /**
     * Đổi số thứ tự của 1 page.
     * Nếu số mới đã có page khác → tự động đẩy page đó lên/xuống.
     *
     * 📌 Quy trình:
     *    1. Tìm page cần đổi số
     *    2. Nếu pageNumber mới == pageNumber cũ → không làm gì
     *    3. Nếu pageNumber mới trùng với page khác
     *       → đẩy các pages bị ảnh hưởng lên/xuống 1 đơn vị
     *    4. Cập nhật pageNumber mới
     *
     * 📌 Ví dụ:
     *    Chapter có pages: số 1 -> id=10, số 2 -> id=20, số 3 -> id=30
     *    => Đổi page 10 từ số 1 sang số 3
     *    => Kết quả: số 1 -> id=20, số 2 -> id=30, số 3 -> id=10
     *
     * @param pageId        ID của page cần đổi số
     * @param newPageNumber Số thứ tự mới
     * @return PageResponse page đã được cập nhật
     * @throws AppException 404 — nếu không tìm thấy page
     */
    public PageResponse updateOrder(Long pageId, Integer newPageNumber) {
        // ── Bước 1: Tìm page cần đổi số ──
        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Page not found"));

        Integer oldPageNumber = page.getPageNumber();

        // ── Bước 2: Nếu số mới == số cũ → không làm gì ──
        if (oldPageNumber.equals(newPageNumber)) {
            return pageMapper.toResponse(page);
        }

        // ── Bước 3: Lấy tất cả pages trong chapter → sắp xếp ──
        List<Page> chapterPages = pageRepository
                .findByChapterIdOrderByPageNumberAsc(page.getChapterId());

        // ── Bước 4: Xoá page hiện tại khỏi list ──
        chapterPages.removeIf(p -> p.getId().equals(pageId));

        // ── Bước 5: Chèn page vào vị trí mới ──
        // newPageNumber - 1 vì index bắt đầu từ 0
        // Ví dụ: chèn vào số 3 → index = 2 (sau phần tử thứ 2)
        int insertIndex = Math.min(newPageNumber - 1, chapterPages.size());
        chapterPages.add(insertIndex, page);

        // ── Bước 6: Gán lại pageNumber từ đầu ──
        AtomicInteger counter = new AtomicInteger(1);
        for (Page p : chapterPages) {
            p.setPageNumber(counter.getAndIncrement());
        }

        // ── Bước 7: Lưu tất cả ──
        pageRepository.saveAll(chapterPages);

        // ── Bước 8: Trả về page đã cập nhật ──
        return pageMapper.toResponse(page);
    }

    // ════════════════════════════════════════════════════════════════
    // 7. MERGE LAYERS — Composite layers → final image
    // ════════════════════════════════════════════════════════════════

    /**
     * Merge tất cả visible layers của 1 page thành 1 ảnh duy nhất.
     * Kết quả upload lên Cloudinary và lưu URL vào page.finalImageUrl.
     *
     * 📌 Quy trình:
     *    1. Tìm page theo id → nếu không có throw 404
     *    2. Lấy layers của page, lọc visible == true, có fileUrl
     *    3. Lấy seriesId từ chapter (cần cho Cloudinary folder path)
     *    4. Download ảnh nền từ page.originalImageUrl
     *    5. Với mỗi layer (từ dưới lên theo sortOrder):
     *       - Download ảnh layer từ fileUrl
     *       - Composite vào ảnh nền với opacity tương ứng
     *    6. Upload ảnh đã composite lên Cloudinary (overwrite)
     *    7. Set page.finalImageUrl = URL ảnh merge
     *    8. Save page → trả về PageResponse
     *
     * 📌 Graphics2D + AlphaComposite:
     *    - AlphaComposite.SrcOver: vẽ layer chồng lên nền
     *    - derive(opacity): set độ trong suốt của layer (0.0 → 1.0)
     *    - RenderingHints.INTERPOLATION_BILINEAR: mượt ảnh
     *
     * @param pageId ID của page cần merge layers
     * @return PageResponse page đã cập nhật finalImageUrl
     * @throws AppException 404 — nếu không tìm thấy page
     */
    @Transactional
    public PageResponse mergeLayers(Long pageId) {
        // ── Bước 1: Tìm page ──
        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Page not found with id: " + pageId));

        // ── Bước 2: Lấy layers visible của page (từ dưới lên) ──
        List<Layer> layers = layerRepository
                .findByPageIdOrderBySortOrderAsc(pageId)
                .stream()
                .filter(l -> l.isVisible() && l.getFileUrl() != null && !l.getFileUrl().isBlank())
                .toList();

        try {
            // ── Bước 4: Download ảnh nền (base page) ──
            // Dùng originalImageUrl — ảnh gốc full size
            BufferedImage baseImage = ImageIO.read(new URL(page.getOriginalImageUrl()));

            // ── Bước 5: Composite từng layer vào ảnh nền ──
            // Duyệt theo sortOrder tăng dần (dưới → trên)
            BufferedImage compositeImage = baseImage;

            for (Layer layer : layers) {
                // Download ảnh layer từ Cloudinary
                BufferedImage layerImage = ImageIO.read(new URL(layer.getFileUrl()));
                if (layerImage == null) continue;

                // Tạo ảnh mới cùng kích thước với ảnh nền
                BufferedImage merged = new BufferedImage(
                        compositeImage.getWidth(),
                        compositeImage.getHeight(),
                        BufferedImage.TYPE_INT_ARGB);

                // Graphics2D — vẽ compositeImage + layerImage chồng lên merged
                Graphics2D g2d = merged.createGraphics();
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);

                // Vẽ ảnh nền trước
                g2d.drawImage(compositeImage, 0, 0, null);

                // Vẽ layer với opacity
                // AlphaComposite.SrcOver: layer chồng lên nền
                // derive((float) opacity): set độ trong suốt
                g2d.setComposite(AlphaComposite.SrcOver.derive((float) layer.getOpacity()));
                g2d.drawImage(layerImage, 0, 0,
                        compositeImage.getWidth(), compositeImage.getHeight(), null);

                g2d.dispose();

                // Giải phóng bộ nhớ native của layerImage ngay sau khi dùng
                layerImage.flush();
                if (compositeImage != baseImage) {
                    compositeImage.flush();
                }

                compositeImage = merged;
            }

            // Giải phóng baseImage
            baseImage.flush();

            // ── Bước 6: Xoá ảnh merge cũ (nếu có) ──
            if (page.getFinalImageUrl() != null) {
                try {
                    cloudinaryService.deleteImageByUrl(page.getFinalImageUrl());
                } catch (Exception ignored) {
                    // Không throw — ưu tiên upload merge mới
                }
            }

            // ── Bước 6: Upload ảnh đã merge lên Cloudinary ──
            CloudinaryService.UploadPageMergeResult mergeResult = cloudinaryService.uploadPageMerge(
                    compositeImage, page.getChapterId(), page.getId());

            // ── Bước 8: Cập nhật URLs của page ──
            page.setFinalImageUrl(mergeResult.getFinalImageUrl());
            page.setWebImageUrl(mergeResult.getWebImageUrl());
            page.setThumbnailUrl(mergeResult.getThumbnailUrl());

            // Giải phóng merge result sau upload
            compositeImage.flush();

            // ── Bước 9: Lưu page và trả về ──
            Page savedPage = pageRepository.save(page);
            return pageMapper.toResponse(savedPage);

        } catch (Exception e) {
            // IOException từ ImageIO.read() hoặc Cloudinary lỗi
            throw new RuntimeException("Failed to merge layers for page " + pageId, e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 8. FLATTEN — Merge + replace original + delete layers
    // ════════════════════════════════════════════════════════════════

    /**
     * Flatten page: gộp tất cả visible layers vào ảnh nền, ghi đè
     * originalImageUrl bằng kết quả merge, xoá toàn bộ layers.
     * <p>
     * 📌 Quy trình:
     *    1. Gọi mergeLayers() — composite + upload + set finalImageUrl
     *    2. Xoá ảnh gốc cũ trên Cloudinary
     *    3. Ghi đè originalImageUrl = finalImageUrl
     *    4. Xoá finalImageUrl (không còn cần thiết)
     *    5. Xoá toàn bộ layers (DB + Cloudinary) qua layerService
     *    6. Lưu page và trả về
     *
     * @param pageId ID của page cần flatten
     * @return PageResponse page đã cập nhật originalImageUrl
     * @throws AppException 404 — nếu không tìm thấy page
     */
    public PageResponse flattenPage(Long pageId) {
        // ── Bước 1: Gọi mergeLayers — composite + upload + set finalImageUrl ──
        mergeLayers(pageId);

        // ── Bước 2: Fetch lại page sau khi merge ──
        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Page not found with id: " + pageId));

        // ── Bước 3: Xoá ảnh gốc cũ trên Cloudinary ──
        if (page.getOriginalImageUrl() != null && !page.getOriginalImageUrl().isBlank()) {
            try {
                cloudinaryService.deleteImageByUrl(page.getOriginalImageUrl());
            } catch (Exception e) {
                // Không throw — ưu tiên hoàn tất flatten hơn là giữ ảnh cũ
            }
        }

        // ── Bước 4: Ghi đè originalImageUrl = finalImageUrl ──
        page.setOriginalImageUrl(page.getFinalImageUrl());
        page.setFinalImageUrl(null);

        // ── Bước 5: Xoá toàn bộ layers (DB + Cloudinary) ──
        layerService.deleteLayersByPage(pageId);

        // ── Bước 6: Lưu page và trả về ──
        Page savedPage = pageRepository.save(page);
        return pageMapper.toResponse(savedPage);
    }
}
