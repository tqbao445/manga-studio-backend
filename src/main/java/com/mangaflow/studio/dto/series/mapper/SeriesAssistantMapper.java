package com.mangaflow.studio.dto.series.mapper;

import com.mangaflow.studio.dto.auth.mapper.UserMapper;
import com.mangaflow.studio.dto.series.response.SeriesAssistantResponse;
import com.mangaflow.studio.model.series.SeriesAssistant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * ── SeriesAssistantMapper ──
 * MapStruct mapper — chuyển đổi giữa Entity SeriesAssistant và DTO SeriesAssistantResponse.
 * <p>
 * 📌 @Mapper(componentModel = "spring"):
 *    MapStruct tự sinh class implementation (SeriesAssistantMapperImpl) lúc compile.
 *    componentModel = "spring" → implementation được đánh @Component,
 *    tự động inject được bằng @Autowired / constructor injection.
 * <p>
 * 📌 uses = UserMapper.class:
 *    Khi mapping nested User → UserDTO (assistant, invitedBy),
 *    MapStruct tự động gọi UserMapper.toDTO().
 *    Không cần viết mapping thủ công.
 * <p>
 * ═══════════════════════════════════════════════════════
 *  1 method mapping:
 * ═══════════════════════════════════════════════════════
 *  toResponse()  SeriesAssistant → SeriesAssistantResponse
 * ═══════════════════════════════════════════════════════
 */
@Mapper(componentModel = "spring", uses = UserMapper.class)
public interface SeriesAssistantMapper {

    /**
     * Chuyển đổi SeriesAssistant entity → SeriesAssistantResponse DTO.
     * <p>
     * 📌 @Mapping rules:
     * ═══════════════════════════════════════════════════════════
     * Target field       │ Cấu hình                     │ Giải thích
     * ═══════════════════════════════════════════════════════════
     * seriesId           │ source = series.id           │ Lấy ID từ entity Series bên trong
     * status             │ expression                   │ Enum → String: .name()
     * assistant          │ (tự động)                    │ User → UserDTO nhờ UserMapper
     * invitedBy          │ (tự động)                    │ User → UserDTO nhờ UserMapper
     * ═══════════════════════════════════════════════════════════
     * Các field cùng tên │ (không cấu hình)             │ id, invitedAt, respondedAt
     *
     * @param seriesAssistant Entity từ database
     * @return SeriesAssistantResponse DTO — không chứa password hay lazy references
     */
    @Mapping(target = "seriesId", source = "series.id")
    @Mapping(target = "status",
             expression = "java(seriesAssistant.getStatus().name())")
    SeriesAssistantResponse toResponse(SeriesAssistant seriesAssistant);
}
