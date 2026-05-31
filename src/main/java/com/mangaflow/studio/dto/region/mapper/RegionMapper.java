package com.mangaflow.studio.dto.region.mapper;

import com.mangaflow.studio.dto.region.response.RegionResponse;
import com.mangaflow.studio.model.region.Region;
import org.mapstruct.Mapper;

/**
 * ── RegionMapper ──
 * MapStruct mapper — chuyển đổi giữa Entity Region và RegionResponse DTO.
 * <p>
 * 📌 @Mapper(componentModel = "spring"):
 *    MapStruct tự động sinh class implementation (RegionMapperImpl) lúc compile.
 *    componentModel = "spring" → implementation được đánh @Component,
 *    tự động inject được bằng @Autowired / constructor injection.
 * <p>
 * ════════════════════════════════════════════════════════════════
 *  MapStruct hoạt động thế nào?
 * ════════════════════════════════════════════════════════════════
 *  Khi bạn compile, MapStruct đọc interface này và tự sinh code:
 * <p>
 *    @Component
 *    public class RegionMapperImpl implements RegionMapper {
 *        public RegionResponse toResponse(Region region) {
 *            if (region == null) return null;
 *            return RegionResponse.builder()
 *                .id(region.getId())
 *                .pageId(region.getPageId())
 *                .regionType(region.getRegionType())
 *                .label(region.getLabel())
 *                .x(region.getX())
 *                .y(region.getY())
 *                .width(region.getWidth())
 *                .height(region.getHeight())
 *                .color(region.getColor())
 *                .sortOrder(region.getSortOrder())
 *                .status(region.getStatus())
 *                .createdAt(region.getCreatedAt())
 *                .build();
 *        }
 *    }
 * <p>
 *  Thay vì bạn phải tự viết code đó bằng tay.
 * <p>
 * ════════════════════════════════════════════════════════════════
 *  Tại sao không cần @Mapping?
 * ════════════════════════════════════════════════════════════════
 *  Vì tất cả field trong Region và RegionResponse đều TRÙNG TÊN:
 * <p>
 *    Region.id         → RegionResponse.id         (cùng tên)
 *    Region.pageId     → RegionResponse.pageId     (cùng tên)
 *    Region.regionType → RegionResponse.regionType (cùng tên)
 *    Region.label      → RegionResponse.label      (cùng tên)
 *    Region.x          → RegionResponse.x          (cùng tên)
 *    Region.y          → RegionResponse.y          (cùng tên)
 *    Region.width      → RegionResponse.width      (cùng tên)
 *    Region.height     → RegionResponse.height     (cùng tên)
 *    Region.color      → RegionResponse.color      (cùng tên)
 *    Region.sortOrder  → RegionResponse.sortOrder  (cùng tên)
 *    Region.status     → RegionResponse.status     (cùng tên)
 *    Region.createdAt  → RegionResponse.createdAt  (cùng tên)
 * <p>
 *  MapStruct tự động map các field cùng tên.
 *  Chỉ cần @Mapping khi tên field KHÁC nhau giữa Entity và DTO.
 * <p>
 * ════════════════════════════════════════════════════════════════
 *  1 method duy nhất:
 * ════════════════════════════════════════════════════════════════
 *  toResponse()  Region → RegionResponse  (Entity → Response DTO)
 * <p>
 *  Dùng ở tất cả các endpoint trả về dữ liệu region:
 *    GET    /api/v1/pages/{pageId}/regions → list regions
 *    POST   /api/v1/pages/{pageId}/regions → region vừa tạo
 *    PUT    /api/v1/regions/{id}           → region đã cập nhật
 *    PATCH  /api/v1/regions/{id}/status    → region sau đổi status
 *    PUT    /api/v1/pages/{pageId}/regions/reorder → list regions đã sắp xếp
 */
@Mapper(componentModel = "spring")
public interface RegionMapper {

    /**
     * Chuyển đổi Region entity → RegionResponse DTO.
     * MapStruct tự động map tất cả field cùng tên.
     * <p>
     * 📌 Null safety: Nếu region = null → trả về null.
     *
     * @param region Entity từ database
     * @return RegionResponse DTO gửi về frontend
     */
    RegionResponse toResponse(Region region);
}
