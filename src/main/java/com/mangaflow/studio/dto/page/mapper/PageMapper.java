package com.mangaflow.studio.dto.page.mapper;

import com.mangaflow.studio.dto.page.response.PageResponse;
import com.mangaflow.studio.model.page.Page;
import org.mapstruct.Mapper;

/**
 * ── PageMapper ──
 * MapStruct mapper — chuyển đổi giữa Entity Page và PageResponse DTO.
 *
 * 📌 @Mapper(componentModel = "spring"):
 *    MapStruct tự động sinh class implementation (PageMapperImpl) lúc compile.
 *    componentModel = "spring" → implementation được đánh @Component,
 *    tự động inject được bằng @Autowired / constructor injection.
 *
 * ════════════════════════════════════════════════════════════════
 *  MapStruct hoạt động thế nào?
 * ════════════════════════════════════════════════════════════════
 *  Khi bạn compile, MapStruct đọc interface này và tự sinh code:
 *
 *    @Component
 *    public class PageMapperImpl implements PageMapper {
 *        public PageResponse toResponse(Page page) {
 *            if (page == null) return null;
 *            return PageResponse.builder()
 *                .id(page.getId())
 *                .chapterId(page.getChapterId())
 *                .pageNumber(page.getPageNumber())
 *                .originalImageUrl(page.getOriginalImageUrl())
 *                .webImageUrl(page.getWebImageUrl())
 *                .thumbnailUrl(page.getThumbnailUrl())
 *                .width(page.getWidth())
 *                .height(page.getHeight())
 *                .status(page.getStatus())
 *                .createdAt(page.getCreatedAt())
 *                .build();
 *        }
 *    }
 *
 *  Thay vì bạn phải tự viết code đó bằng tay cho mỗi lần map.
 *
 * ════════════════════════════════════════════════════════════════
 *  Tại sao không cần @Mapping?
 * ════════════════════════════════════════════════════════════════
 *  Vì tất cả field trong Page và PageResponse đều TRÙNG TÊN:
 *
 *    Page.id         → PageResponse.id           (cùng tên)
 *    Page.chapterId  → PageResponse.chapterId    (cùng tên)
 *    Page.pageNumber → PageResponse.pageNumber   (cùng tên)
 *    ...
 *
 *  MapStruct tự động map các field cùng tên.
 *  Chỉ cần @Mapping khi tên field KHÁC nhau giữa Entity và DTO.
 *
 * ════════════════════════════════════════════════════════════════
 *  1 method duy nhất:
 * ════════════════════════════════════════════════════════════════
 *  toResponse()  Page → PageResponse  (Entity → Response DTO)
 *
 *  Dùng ở tất cả các endpoint trả về dữ liệu page:
 *    GET /api/v1/chapters/{chapterId}/pages → list pages
 *    POST upload page → trả về page vừa tạo
 *    PUT reorder → trả về page đã đổi số
 */
@Mapper(componentModel = "spring")
public interface PageMapper {

    /**
     * Chuyển đổi Page entity → PageResponse DTO.
     * MapStruct tự động map tất cả field cùng tên.
     *
     * 📌 Null safety: Nếu page = null → trả về null.
     *
     * @param page Entity từ database
     * @return PageResponse DTO gửi về frontend
     */
    PageResponse toResponse(Page page);
}
