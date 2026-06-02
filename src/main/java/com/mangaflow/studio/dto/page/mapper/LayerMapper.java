package com.mangaflow.studio.dto.page.mapper;

import com.mangaflow.studio.dto.page.request.LayerRequest;
import com.mangaflow.studio.dto.page.response.LayerResponse;
import com.mangaflow.studio.dto.task.response.UserBasicDTO;
import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.model.page.Layer;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * ── LayerMapper ──
 * MapStruct mapper — chuyển đổi giữa Entity Layer và LayerResponse DTO.
 * <p>
 * 📌 @Mapper(componentModel = "spring"):
 *    MapStruct tự động sinh class implementation (LayerMapperImpl) lúc compile.
 *    componentModel = "spring" → implementation được đánh @Component,
 *    tự động inject được bằng @Autowired / constructor injection.
 * <p>
 * ════════════════════════════════════════════════════════════════
 *  Các mapping đặc biệt:
 * ════════════════════════════════════════════════════════════════
 * <p>
 *  1. createdBy: User entity → UserBasicDTO
 *     → Dùng default method toBasicUser() để map thủ công
 *     → Chỉ lấy id, displayName, avatarUrl
 * <p>
 *  2. toEntity(): LayerRequest → Layer Entity (tạo mới)
 *     → pageId, sortOrder, opacity, visible, blendMode, locked có default
 * <p>
 *  3. updateFromRequest(): LayerRequest → Layer (cập nhật từng phần)
 *     → @BeanMapping với NullValuePropertyMappingStrategy.IGNORE
 *     → Chỉ cập nhật field KHÔNG null trong request
 */
@Mapper(componentModel = "spring")
public interface LayerMapper {

    /**
     * Chuyển đổi Layer entity → LayerResponse DTO.
     * <p>
     * 📌 @Mapping(target = "createdBy", expression = "java(toBasicUser(layer.getCreatedBy()))"):
     *    Gọi method default để map User entity → UserBasicDTO.
     *    Chỉ lấy id, displayName, avatarUrl.
     *
     * @param layer Entity từ database
     * @return LayerResponse DTO gửi về frontend
     */
    @Mapping(target = "createdBy", expression = "java(toBasicUser(layer.getCreatedBy()))")
    LayerResponse toResponse(Layer layer);

    /**
     * Chuyển đổi LayerRequest → Layer Entity (dùng khi tạo mới).
     * <p>
     * 📌 @Mapping(target = "id", ignore = true):
     *    ID do DB tự sinh (IDENTITY).
     * <p>
     * 📌 @Mapping(target = "createdBy", ignore = true):
     *    User được set trong Service (lấy từ SecurityContext).
     * <p>
     * 📌 @Mapping(target = "createdAt", ignore = true):
     *    Thời gian tạo set trong Service (LocalDateTime.now()).
     * <p>
     * 📌 Các field không có @Mapping:
     *    label, fileUrl, thumbnailUrl, sortOrder, opacity,
     *    visible, blendMode, locked — tự động map vì cùng tên.
     *    Nếu request gửi null → MapStruct giữ null ⇒ DB lấy default.
     *
     * @param request DTO từ client
     * @return Layer entity (chưa có id, createdBy, createdAt)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "page", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Layer toEntity(LayerRequest request);

    /**
     * Cập nhật Layer entity từ LayerRequest (chỉ cập nhật field không null).
     * <p>
     * 📌 @BeanMapping(nullValuePropertyMappingStrategy = IGNORE):
     *    Nếu request.label = null → giữ nguyên label cũ.
     *    Nếu request.opacity = null → giữ nguyên opacity cũ.
     *    ...tương tự cho tất cả field.
     * <p>
     * 📌 Dùng với @MappingTarget:
     *    MapStruct sẽ mutate entity có sẵn, không tạo mới.
     *
     * @param request DTO từ client (có thể chỉ gửi 1-2 field cần update)
     * @param layer   Entity hiện tại (từ DB)
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "page", ignore = true)
    void updateFromRequest(LayerRequest request, @MappingTarget Layer layer);

    /**
     * Method default — chuyển User entity → UserBasicDTO.
     * <p>
     * 📌 Đây là default method trong interface (Java 8+).
     *    MapStruct sẽ giữ nguyên method này trong implementation.
     * <p>
     * 📌 Chỉ lấy 3 field: id, displayName, avatarUrl.
     *    Không chứa thông tin nhạy cảm (email, password, role...).
     * <p>
     * 📌 Null safety: nếu user = null → trả về null.
     *
     * @param user User entity (có thể null từ DB)
     * @return UserBasicDTO hoặc null
     */
    default UserBasicDTO toBasicUser(User user) {
        if (user == null) return null;
        return UserBasicDTO.builder()
                .id(user.getId())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }
}
