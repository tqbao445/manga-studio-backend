package com.mangaflow.studio.dto.series.mapper;

import com.mangaflow.studio.dto.auth.mapper.UserMapper;
import com.mangaflow.studio.dto.series.response.SeriesTantouResponse;
import com.mangaflow.studio.model.series.SeriesTantouInvitation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * ── SeriesTantouMapper ──
 * MapStruct mapper — chuyển đổi giữa Entity SeriesTantouInvitation và DTO SeriesTantouResponse.
 * <p>
 * 📌 @Mapper(componentModel = "spring"):
 *    MapStruct tự sinh class implementation (SeriesTantouMapperImpl) lúc compile.
 *    componentModel = "spring" → implementation được đánh @Component,
 *    tự động inject được bằng @Autowired / constructor injection.
 * <p>
 * 📌 uses = UserMapper.class:
 *    Khi mapping nested User → UserDTO (tantou, invitedBy),
 *    MapStruct tự động gọi UserMapper.toDTO().
 *    Không cần viết mapping thủ công.
 * <p>
 * ═══════════════════════════════════════════════════════
 *  1 method mapping:
 * ═══════════════════════════════════════════════════════
 *  toResponse()  SeriesTantouInvitation → SeriesTantouResponse
 * ═══════════════════════════════════════════════════════
 */
@Mapper(componentModel = "spring", uses = UserMapper.class)
public interface SeriesTantouMapper {

    /**
     * Chuyển đổi SeriesTantouInvitation entity → SeriesTantouResponse DTO.
     * <p>
     * 📌 @Mapping rules:
     * ═══════════════════════════════════════════════════════════
     * Target field       │ Cấu hình                     │ Giải thích
     * ═══════════════════════════════════════════════════════════
     * seriesId           │ source = series.id           │ Lấy ID từ entity Series bên trong
     * status             │ expression                   │ Enum → String: .name()
     * tantou             │ (tự động)                    │ User → UserDTO nhờ UserMapper
     * invitedBy          │ (tự động)                    │ User → UserDTO nhờ UserMapper
     * ═══════════════════════════════════════════════════════════
     * Các field cùng tên │ (không cấu hình)             │ id, invitedAt, respondedAt
     *
     * @param invitation Entity từ database
     * @return SeriesTantouResponse DTO — không chứa password hay lazy references
     */
    @Mapping(target = "seriesId", source = "series.id")
    @Mapping(target = "status",
             expression = "java(invitation.getStatus().name())")
    SeriesTantouResponse toResponse(SeriesTantouInvitation invitation);
}
