package com.mangaflow.studio.dto.series.mapper;

import com.mangaflow.studio.dto.auth.mapper.UserMapper;
import com.mangaflow.studio.dto.series.request.SeriesRequest;
import com.mangaflow.studio.dto.series.response.SeriesResponse;
import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.model.series.Series;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * ── SeriesMapper ──
 * MapStruct mapper — chuyển đổi giữa Entity Series và các DTO.
 *
 * 📌 @Mapper(componentModel = "spring"):
 *    MapStruct tự sinh class implementation (SeriesMapperImpl) lúc compile.
 *    componentModel = "spring" → implementation được đánh @Component,
 *    tự động inject được bằng @Autowired / constructor injection.
 *
 * 📌 uses = UserMapper.class:
 *    Khi mapping nested User → UserDTO, MapStruct tự động gọi UserMapper.toDTO().
 *    Không cần viết mapping thủ công cho mangaka và tantouEditor.
 *
 * 📌 MapStruct sinh implementation tại:
 *    target/generated-sources/annotations/.../SeriesMapperImpl.java
 *
 * ═══════════════════════════════════════════════════════
 *  3 methods mapping:
 * ═══════════════════════════════════════════════════════
 *  toResponse()      Series → SeriesResponse      (Entity → Response DTO)
 *  toEntity()        SeriesRequest → Series       (Request → Entity, create)
 *  updateEntity()    SeriesRequest → @MappingTarget Series (Request → merge Entity, update)
 * ═══════════════════════════════════════════════════════
 */
@Mapper(componentModel = "spring", uses = UserMapper.class)
public interface SeriesMapper {

    // ════════════════════════════════════════════════════════════
    // 1. TO RESPONSE — Entity → DTO (READ)
    // ════════════════════════════════════════════════════════════

    /**
     * Chuyển đổi Series entity → SeriesResponse DTO.
     *
     * 📌 Dùng ở tất cả các endpoint trả về dữ liệu:
     *    GET /api/series, GET /api/series/{id}, POST, PUT, submit, approve,...
     *
     * 📌 MapStruct tự động xử lý:
     *    - Enum → String: genre (Genre), targetDemographic (TargetDemographic), status (SeriesStatus)
     *      → tự gọi .name() vì source là enum, target là String.
     *    - Nested User → UserDTO: mangaka, tantouEditor
     *      → tự gọi UserMapper.toDTO() nhờ @Mapper(uses = UserMapper.class).
     *    - Null safety: tantouEditor có thể null → MapStruct tự xử lý, trả về null.
     *    - Các field cùng tên (id, title, coverColor, ...): tự map không cần cấu hình.
     *
     * 📌 Không cần @Mapping nào — vì field names giống nhau giữa Entity và DTO:
     *    Series.id         → SeriesResponse.id        (cùng tên)
     *    Series.title      → SeriesResponse.title     (cùng tên)
     *    Series.genre      → SeriesResponse.genre     (enum→String tự động)
     *    Series.mangaka    → SeriesResponse.mangaka   (User→UserDTO nhờ UserMapper)
     *
     * @param series Entity từ database
     * @return SeriesResponse DTO không chứa password hay lazy references
     */
    SeriesResponse toResponse(Series series);

    // ════════════════════════════════════════════════════════════
    // 2. TO ENTITY — Request → Entity (CREATE)
    // ════════════════════════════════════════════════════════════

    /**
     * Chuyển đổi SeriesRequest DTO → Series entity (dùng khi tạo mới).
     *
     * 📌 Dùng ở POST /api/series — SeriesService.create().
     *
     * 📌 @Mapping rules:
     * ═══════════════════════════════════════════════════════════
     * Field              │ Cấu hình           │ Giải thích
     * ═══════════════════════════════════════════════════════════
     * id                 │ ignore = true      │ DB tự sinh (IDENTITY), không nhận từ request
     * status             │ constant = "DRAFT" │ Mặc định DRAFT — client không được chọn status
     * isMature           │ expression         │ null → false (Boolean wrapper an toàn)
     * mangaka            │ source = mangaka   │ Lấy từ tham số thứ 2 của method
     * tantouEditor       │ ignore = true      │ Chưa có editor khi tạo mới
     * chapterCount       │ ignore = true      │ Denormalized — module Chapter sau này quản lý
     * currentRank        │ ignore = true      │ Denormalized — module Ranking sau này quản lý
     * currentTier        │ ignore = true      │ Denormalized — module Ranking sau này quản lý
     * createdAt          │ ignore = true      │ @PrePersist tự động set
     * updatedAt          │ ignore = true      │ @PrePersist tự động set
     * ═══════════════════════════════════════════════════════════
     * Các field còn lại  │ (không cấu hình)   │ Tự động map vì cùng tên với SeriesRequest
     *                    │                    │ title, titleJp, synopsis, genre,
     *                    │                    │ targetDemographic, coverColor, coverImageUrl
     *
     * 📌 Expression: isMature
     *    expression = "java(request.getIsMature() != null && request.getIsMature())"
     *    - request.getIsMature() == null → false (mặc định không 18+)
     *    - request.getIsMature() == false → false
     *    - request.getIsMature() == true → true
     *    ⚠️ Java expression trong @Mapping phải là Java code hợp lệ, MapStruct gen vào impl.
     *
     * @param request DTO từ client (chứa title, genre, ...)
     * @param mangaka User entity — tác giả (lấy từ user đang đăng nhập)
     * @return Series entity sẵn sàng để repository.save()
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", constant = "DRAFT")
    @Mapping(target = "isMature",
             expression = "java(request.getIsMature() != null && request.getIsMature())")
    @Mapping(target = "mangaka", source = "mangaka")
    @Mapping(target = "tantouEditor", ignore = true)
    @Mapping(target = "chapterCount", ignore = true)
    @Mapping(target = "currentRank", ignore = true)
    @Mapping(target = "currentTier", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Series toEntity(SeriesRequest request, User mangaka);

    // ════════════════════════════════════════════════════════════
    // 3. UPDATE ENTITY — Request → merge Entity (UPDATE)
    // ════════════════════════════════════════════════════════════

    /**
     * Cập nhật Series entity từ SeriesRequest (dùng khi sửa).
     *
     * 📌 Dùng ở PUT /api/series/{id} — SeriesService.update().
     *
     * 📌 @MappingTarget:
     *    MapStruct sẽ thay đổi TRỰC TIẾP trên đối tượng series được truyền vào,
     *    không tạo entity mới. Giống hệt series.setXxx(request.getXxx()).
     *
     * 📌 NullValuePropertyMappingStrategy.IGNORE:
     *    CHỈ update field KHÁC NULL trong request.
     *    Field NULL → bỏ qua, giữ nguyên giá trị cũ trong entity.
     *    Đây là cách hiệu quả nhất để implement null-safe update pattern
     *    mà không cần viết 8 dòng if (title != null) series.setTitle().
     *
     * ═══════════════════════════════════════════════════════════
     * Ví dụ: request chỉ gửi { "title": "New Title" }
     * → MapStruct chỉ set series.title = "New Title"
     * → Các field khác (synopsis, genre, coverColor, ...) giữ nguyên.
     * ═══════════════════════════════════════════════════════════
     *
     * 📌 Các field ignore (không được update qua endpoint này):
     *    - id:              không đổi khoá chính
     *    - status:          dùng submit/approve/reject/updateStatus riêng
     *    - mangaka:         không thể đổi chủ sở hữu
     *    - tantouEditor:    do Editorial Board gán khi approve
     *    - chapterCount, currentRank, currentTier: denormalized
     *    - createdAt, updatedAt: lifecycle callback tự động
     *
     * 📌 Lưu ý về Boolean isMature với IGNORE strategy:
     *    Nếu client gửi isMature = null → bỏ qua (giữ nguyên).
     *    Nếu client gửi isMature = true/false → set giá trị mới.
     *    Không cần expression kiểm tra null như toEntity() vì IGNORE đã xử lý.
     *
     * @param series  Entity hiện tại (sẽ bị mutate — @MappingTarget)
     * @param request DTO chứa các field muốn thay đổi (null → giữ nguyên)
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "mangaka", ignore = true)
    @Mapping(target = "tantouEditor", ignore = true)
    @Mapping(target = "chapterCount", ignore = true)
    @Mapping(target = "currentRank", ignore = true)
    @Mapping(target = "currentTier", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(@MappingTarget Series series, SeriesRequest request);
}
