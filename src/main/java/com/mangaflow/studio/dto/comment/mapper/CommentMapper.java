package com.mangaflow.studio.dto.comment.mapper;

import com.mangaflow.studio.dto.comment.request.CommentRequest;
import com.mangaflow.studio.dto.comment.response.CommentResponse;
import com.mangaflow.studio.model.comment.Comment;
import org.mapstruct.*;

/**
 * ── CommentMapper ──
 * MapStruct mapper — chuyển đổi giữa Entity Comment và CommentResponse DTO.
 *
 * 📌 @Mapper(componentModel = "spring"):
 *    MapStruct tự động sinh class implementation (CommentMapperImpl) lúc compile.
 *    componentModel = "spring" → implementation được đánh @Component,
 *    tự động inject được bằng @Autowired / constructor injection.
 *
 * ════════════════════════════════════════════════════════════════
 *  2 methods:
 * ════════════════════════════════════════════════════════════════
 *
 *  1. toResponse(Comment) → CommentResponse
 *     Entity → Response DTO (dùng cho GET endpoints)
 *     Tự động map: entity.field → response.field (cùng tên)
 *     Đặc biệt: lấy authorId, authorName, authorAvatar từ entity.author
 *               lấy parentId từ entity.parent.id
 *     Bỏ qua replies (Service tự populate)
 *
 *  2. updateEntity(Comment, CommentRequest) → void
 *     Dùng cho PUT — cập nhật entity từ request (null-safe)
 *     Chỉ update các field KHÔNG null trong request
 *     Bỏ qua id, author, parent, pageId, status, timestamps
 *
 * ════════════════════════════════════════════════════════════════
 *  KHÔNG có toEntity():
 * ════════════════════════════════════════════════════════════════
 *  Việc tạo Comment entity mới được thực hiện bằng Builder
 *  trong Service (giống RegionService pattern).
 *  Lý do: cần set nhiều field đặc biệt (author, parent, status, ...)
 *  mà MapStruct khó xử lý linh hoạt.
 */
@Mapper(componentModel = "spring")
public interface CommentMapper {

    /**
     * ── toResponse ──
     * Chuyển đổi Comment entity → CommentResponse DTO.
     *
     * 📌 Auto-map (cùng tên field):
     *    id → id, content → content, pageId → pageId,
     *    status → status, posX → posX, ..., createdAt → createdAt
     *
     * 📌 MapStruct tự động xử lý:
     *    - author.id       → authorId       (Lấy ID từ entity User)
     *    - author.displayName → authorName  (Lấy displayName từ entity User)
     *    - author.avatarUrl → authorAvatar  (Lấy avatarUrl từ entity User)
     *    - parent.id       → parentId       (null nếu parent = null)
     *
     * 📌 replies = ignore:
     *    Replies được Service populate riêng
     *    (commentRepository.findByParentId()) — tránh đệ quy vô hạn.
     *
     * @param comment Entity từ database
     * @return CommentResponse DTO gửi về frontend
     */
    @Mapping(target = "authorId", source = "author.id")
    @Mapping(target = "authorName", source = "author.displayName")
    @Mapping(target = "authorAvatar", source = "author.avatarUrl")
    @Mapping(target = "parentId", source = "parent.id")
    @Mapping(target = "replies", ignore = true)
    CommentResponse toResponse(Comment comment);

    /**
     * ── updateEntity ──
     * Cập nhật Comment entity từ CommentRequest (partial update).
     * Null-safe: chỉ update field KHÁC NULL trong request.
     *
     * 📌 @BeanMapping(nullValuePropertyMappingStrategy = IGNORE):
     *    Nếu request.content = null → bỏ qua, giữ nguyên content cũ.
     *    Nếu request.content = "mới" → cập nhật content.
     *
     * 📌 ignore:
     *    - id: không đổi khoá chính
     *    - author: không thay đổi người viết
     *    - parent: không thay đổi parent (reply không thể đổi cha)
     *    - pageId: không thể đổi page
     *    - status: dùng method riêng (updateStatus)
     *    - createdAt/updatedAt: tự động quản lý bởi JPA callback
     *
     * @param comment Entity cần cập nhật (từ DB)
     * @param request DTO chứa field muốn thay đổi (các field null bỏ qua)
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "pageId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(@MappingTarget Comment comment, CommentRequest request);
}
