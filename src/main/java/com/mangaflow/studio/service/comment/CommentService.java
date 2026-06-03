package com.mangaflow.studio.service.comment;

import com.mangaflow.studio.common.exception.AppException;
import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.comment.mapper.CommentMapper;
import com.mangaflow.studio.dto.comment.request.CommentRequest;
import com.mangaflow.studio.dto.comment.response.CommentResponse;
import com.mangaflow.studio.model.comment.Comment;
import com.mangaflow.studio.model.comment.CommentStatus;
import com.mangaflow.studio.repository.comment.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ── CommentService ──
 * Service xử lý toàn bộ logic nghiệp vụ liên quan đến Comment.
 * Là tầng trung gian giữa CommentController (API) và CommentRepository (DB).
 *
 * 📌 @Service: Spring Bean — chứa business logic, quản lý transaction.
 * 📌 @RequiredArgsConstructor: Lombok — DI constructor cho tất cả field final.
 *
 * ══════════════════════════════════════════════════════════════════
 *  Danh sách method:
 * ══════════════════════════════════════════════════════════════════
 *
 *  1. getByPage(pageId)               — DS comment gốc + replies
 *  2. create(pageId, request, user)   — Tạo comment mới
 *  3. reply(parentId, request, user)  — Trả lời 1 comment
 *  4. getById(id)                     — Chi tiết 1 comment + replies
 *  5. update(id, request, user)       — Sửa nội dung
 *  6. delete(id, user)                — Xoá comment + replies con
 *  7. updateStatus(id, status, user)  — Resolve / Reopen
 *
 * ══════════════════════════════════════════════════════════════════
 *  Luồng dữ liệu tổng quát:
 * ══════════════════════════════════════════════════════════════════
 *
 *  [Frontend]
 *    │  HTTP Request (JSON + JWT)
 *    ▼
 *  [CommentController]
 *    │ @PreAuthorize kiểm tra role
 *    │ Gọi Service method
 *    ▼
 *  [CommentService]  ← BẠN ĐANG Ở ĐÂY
 *    │ Kiểm tra logic nghiệp vụ
 *    │ Gọi Repository / Mapper
 *    ▼
 *  [CommentRepository]
 *    │ JPA → SQL → DB
 *    ▼
 *  [Database]
 *    │
 *    ▼
 *  Response JSON về Frontend
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {

    // ════════════════════════════════════════════════════════════════
    // DI — Các dependency được inject qua constructor
    // ════════════════════════════════════════════════════════════════

    /**
     * commentRepository: Repository chính — thao tác với bảng comments.
     * Cung cấp CRUD + method tuỳ chỉnh findByPageIdAndParentIsNull...
     */
    private final CommentRepository commentRepository;

    /**
     * commentMapper: MapStruct — chuyển đổi Comment entity ↔ CommentResponse DTO.
     * Tự động map các field cùng tên, đồng thời flat author và parent.
     */
    private final CommentMapper commentMapper;

    // ════════════════════════════════════════════════════════════════
    // 1. GET COMMENTS BY PAGE — Lấy danh sách comments
    // ════════════════════════════════════════════════════════════════

    /**
     * Lấy danh sách comment GỐC (parent = null) của 1 page,
     * mỗi comment gốc kèm theo các replies của nó.
     *
     * 📌 @Transactional(readOnly = true):
     *    - Không cần dirty checking → Hibernate bỏ qua cơ chế flush.
     *    - Tối ưu hiệu năng — không cần transaction write lock.
     *
     * 📌 Cấu trúc dữ liệu trả về (dạng cây, 1 cấp):
     *    [
     *      {  ← comment gốc 1
     *        "id": 1,
     *        "content": "Sửa chỗ A",
     *        "parentId": null,     ← null = comment gốC
     *        "posX": 150.0,
     *        "posY": 200.0,
     *        "replies": [          ← replies của comment gốc 1
     *          { "id": 2, "parentId": 1, "content": "OK" },
     *          { "id": 3, "parentId": 1, "content": "Xong" }
     *        ]
     *      },
     *      {  ← comment gốc 2
     *        "id": 4,
     *        "content": "Sửa chỗ B",
     *        "parentId": null,
     *        "posX": 300.0,
     *        "posY": 400.0,
     *        "replies": [ ... ]
     *      }
     *    ]
     *
     * 📌 Cách load replies:
     *    - Dùng commentRepository.findByParentId() riêng cho từng comment gốc.
     *    - Đây là N+1 query (N = số comment gốc).
     *    - Với số lượng comment nhỏ (< 100) thì không vấn đề.
     *    - Nếu sau này nhiều comment → có thể tối ưu bằng @EntityGraph
     *      hoặc 1 query SELECT * WHERE page_id = ? rồi group trong Java.
     *
     * @param pageId ID của page cần lấy comments
     * @return List<CommentResponse> danh sách comments gốc, mỗi cái kèm replies
     */
    @Transactional(readOnly = true)
    public List<CommentResponse> getByPage(Long pageId) {
        // ── Bước 1: Lấy tất cả comment gốc của page ──
        // WHERE page_id = ? AND parent_id IS NULL ORDER BY created_at ASC
        List<Comment> rootComments = commentRepository
                .findByPageIdAndParentIsNullOrderByCreatedAtAsc(pageId);

        // ── Bước 2: Với mỗi comment gốc ──
        //   a) Map entity → response DTO
        //   b) Tìm replies của nó trong DB
        //   c) Map replies → response DTO
        //   d) Gán replies vào response của comment gốc
        return rootComments.stream()
                .map(this::buildCommentTree)  // Method reference: tự populate replies
                .toList();
    }

    // ════════════════════════════════════════════════════════════════
    // 2. GET COMMENT BY ID — Chi tiết 1 comment + replies
    // ════════════════════════════════════════════════════════════════

    /**
     * Lấy chi tiết 1 comment + replies của nó (nếu có).
     *
     * 📌 @Transactional(readOnly = true):
     *    - Tối ưu hiệu năng cho read-only operation.
     *
     * 📌 Quy trình:
     *    1. Tìm comment theo ID
     *       - Nếu không tìm thấy → throw 404
     *    2. Map entity → response DTO
     *    3. Tìm replies → map → gán vào response
     *    4. Trả về
     *
     * @param id ID của comment cần lấy
     * @return CommentResponse chi tiết comment + replies
     * @throws AppException 404 — nếu không tìm thấy comment
     */
    @Transactional(readOnly = true)
    public CommentResponse getById(Long id) {
        // ── Bước 1: Tìm comment ──
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Comment not found with id: " + id));

        // ── Bước 2: Map + populate replies ──
        return buildCommentTree(comment);
    }

    // ════════════════════════════════════════════════════════════════
    // 3. CREATE COMMENT — Tạo comment mới
    // ════════════════════════════════════════════════════════════════

    /**
     * Tạo comment mới trên 1 page.
     *
     * 📌 Quy trình (6 bước):
     *
     *    ┌──────────┐
     *    │ Bước 1   │ Kiểm tra parentId (nếu có)
     *    │          │ → Nếu parentId != null → đây là reply
     *    │          │ → Tìm parent comment → nếu không tìm thấy → 404
     *    └────┬─────┘
     *         ▼
     *    ┌──────────┐
     *    │ Bước 2   │ Tạo Comment entity bằng Builder
     *    │          │ - content: từ request
     *    │          │ - author: user hiện tại
     *    │          │ - pageId: từ URL path
     *    │          │ - parent: null (nếu là comment gốc) hoặc entity parent
     *    │          │ - status: ACTIVE
     *    │          │ - posX, posY, posWidth, posHeight: từ request
     *    └────┬─────┘
     *         ▼
     *    ┌──────────┐
     *    │ Bước 3   | Lưu xuống database
     *    │          │ → INSERT INTO comments (...)
     *    │          │ → SQL Server tự sinh id
     *    └────┬─────┘
     *         ▼
     *    ┌──────────┐
     *    │ Bước 4   │ Map entity → response DTO
     *    │          │ + populate replies (rỗng nếu mới tạo)
     *    │          │ → Trả về CommentResponse cho Controller
     *    └──────────┘
     *
     * 📌 @Transactional (class-level):
     *    Toàn bộ các bước chạy trong 1 transaction.
     *    Nếu bất kỳ bước nào fail → tất cả rollback.
     *
     * @param pageId  ID của page muốn comment
     * @param request DTO từ client: content, parentId, posX, posY, posWidth, posHeight
     * @param user    User đang đăng nhập (lấy userId để gán author)
     * @return CommentResponse — comment vừa tạo
     * @throws AppException 404 — nếu parent comment không tồn tại
     */
    public CommentResponse create(Long pageId, CommentRequest request, CustomUserDetails user) {
        // ── Bước 1: Kiểm tra parentId (nếu có) ──
        // Nếu request có parentId → người dùng đang reply
        // Cần kiểm tra parent comment có tồn tại không
        Comment parent = null;
        if (request.getParentId() != null) {
            parent = commentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                            "Parent comment not found with id: " + request.getParentId()));
        }

        // ── Bước 2: Tạo Comment entity bằng Builder ──
        Comment comment = Comment.builder()
                .content(request.getContent())            // Nội dung comment
                .author(user.getUser())                    // Entity User (lấy từ JWT token)
                .pageId(pageId)                            // Page chứa comment
                .parent(parent)                            // null = gốc, có giá trị = reply
                .status(CommentStatus.ACTIVE)             // Mặc định ACTIVE
                .posX(request.getPosX())                   // Toạ độ annotation (có thể null)
                .posY(request.getPosY())
                .posWidth(request.getPosWidth())
                .posHeight(request.getPosHeight())
                .build();                                  // Tạo entity

        // ── Bước 3: Lưu xuống database ──
        // INSERT INTO comments (content, author_id, page_id, parent_id,
        //                       status, pos_x, pos_y, pos_width, pos_height,
        //                       created_at, updated_at)
        // VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, NOW(), NOW())
        Comment savedComment = commentRepository.save(comment);

        // ── Bước 4: Map entity → response DTO + populate replies (rỗng) ──
        return buildCommentTree(savedComment);
    }

    // ════════════════════════════════════════════════════════════════
    // 4. REPLY — Trả lời 1 comment
    // ════════════════════════════════════════════════════════════════

    /**
     * Tạo reply cho 1 comment có sẵn.
     *
     * 📌 Khác với create() ở chỗ:
     *    - parentId được lấy từ URL path, không phải từ request body.
     *    - Frontend gọi: POST /api/v1/comments/{parentId}/replies
     *    - Reply kế thừa pageId từ comment cha.
     *
     * 📌 Thực chất là:
     *    Gọi create() với pageId lấy từ comment cha và parentId có sẵn.
     *
     * 📌 Quy trình:
     *    1. Tìm parent comment → lấy pageId từ nó
     *    2. Redirect sang create() với parentId đã set
     *
     * @param parentId ID của comment cha (lấy từ URL)
     * @param request  DTO chỉ chứa content (không cần toạ độ)
     * @param user     User đang đăng nhập
     * @return CommentResponse — reply vừa tạo
     * @throws AppException 404 — nếu parent comment không tồn tại
     */
    public CommentResponse reply(Long parentId, CommentRequest request, CustomUserDetails user) {
        // ── Bước 1: Tìm parent comment ──
        Comment parent = commentRepository.findById(parentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Parent comment not found with id: " + parentId));

        // ── Bước 2: Set parentId vào request và gọi create() ──
        // Reply kế thừa pageId từ comment cha
        request.setParentId(parentId);
        return create(parent.getPageId(), request, user);
    }

    // ════════════════════════════════════════════════════════════════
    // 5. UPDATE COMMENT — Cập nhật nội dung
    // ════════════════════════════════════════════════════════════════

    /**
     * Cập nhật nội dung comment. Chỉ AUTHOR mới được cập nhật.
     *
     * 📌 Quy trình:
     *    1. Tìm comment theo id + authorId (ownership check)
     *       - Nếu không tìm thấy → 404 (không tiết lộ lý do cụ thể)
     *    2. MapStruct null-safe update
     *       - Chỉ cập nhật content (các field khác bị ignore)
     *    3. Lưu lại → Hibernate tự động UPDATE
     *    4. Map sang DTO + populate replies → trả về
     *
     * 📌 @BeanMapping(nullValuePropertyMappingStrategy = IGNORE):
     *    Trong Mapper, nếu request.content = null → giữ nguyên content cũ.
     *    Nhưng ở đây request đã được @Valid validate → content không thể null.
     *
     * 📌 Chỉ sửa được content:
     *    - Không sửa được toạ độ (posX, posY...)
     *    - Không sửa được parent (không thể đổi cha)
     *    - Không sửa được status (dùng PATCH status riêng)
     *
     * @param id      ID của comment cần sửa
     * @param request DTO chứa content mới
     * @param user    User đang đăng nhập (kiểm tra ownership)
     * @return CommentResponse — comment sau khi cập nhật
     * @throws AppException 404 — nếu không tìm thấy hoặc không phải chủ
     */
    public CommentResponse update(Long id, CommentRequest request, CustomUserDetails user) {
        // ── Bước 1: Ownership check ──
        // findByIdAndAuthorId() kiểm tra đồng thời:
        //   1. Comment có tồn tại không?
        //   2. User hiện tại có phải tác giả không?
        Comment comment = commentRepository.findByIdAndAuthorId(id, user.getUserId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Comment not found or not owned by you"));

        // ── Bước 2: MapStruct null-safe update ──
        // updateEntity() chỉ cập nhật content (các field khác bị ignore)
        // → Không cần viết: if (content != null) comment.setContent(content)
        commentMapper.updateEntity(comment, request);

        // ── Bước 3: Lưu thay đổi ──
        // JPA detect changes tự động nhờ @Transactional,
        // nhưng gọi save() để chắc chắn flush xuống DB
        Comment savedComment = commentRepository.save(comment);

        // ── Bước 4: Map entity → response DTO ──
        return buildCommentTree(savedComment);
    }

    // ════════════════════════════════════════════════════════════════
    // 6. DELETE COMMENT — Xoá comment
    // ════════════════════════════════════════════════════════════════

    /**
     * Xoá 1 comment. Chỉ AUTHOR mới được xoá.
     * Khi xoá comment gốc → tự động xoá luôn tất cả replies của nó.
     *
     * 📌 Quy trình xoá comment gốc:
     *    1. Tìm comment → kiểm tra ownership
     *    2. Tìm tất cả replies của comment này (WHERE parent_id = ?)
     *    3. Xoá tất cả replies trước
     *    4. Xoá comment gốc
     *
     * 📌 Quy trình xoá reply:
     *    1. Tìm comment → kiểm tra ownership
     *    2. Xoá luôn (reply không có replies con)
     *
     * 📌 Không hỗ trợ cascade xoá:
     *    Entity KHÔNG có @OneToMany(replies) → không có cascade.
     *    Phải xoá replies thủ công.
     *
     * @param id   ID của comment cần xoá
     * @param user User đang đăng nhập (kiểm tra ownership)
     * @throws AppException 404 — nếu không tìm thấy hoặc không phải chủ
     */
    public void delete(Long id, CustomUserDetails user) {
        // ── Bước 1: Ownership check ──
        Comment comment = commentRepository.findByIdAndAuthorId(id, user.getUserId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Comment not found or not owned by you"));

        // ── Bước 2: Kiểm tra nếu là comment gốc → xoá replies trước ──
        // Nếu comment này là gốc (parent == null), nó có thể có replies con
        // Phải xoá replies trước, sau đó mới xoá được comment gốc
        // (Do ràng buộc khoá ngoại trong DB: parent_id REFERENCES comment(id))
        if (comment.getParent() == null) {
            // Tìm tất cả replies của comment gốc
            List<Comment> replies = commentRepository.findByParentIdOrderByCreatedAtAsc(id);
            if (!replies.isEmpty()) {
                // Xoá tất cả replies trước
                // DELETE FROM comments WHERE parent_id = ?
                commentRepository.deleteAll(replies);
            }
        }

        // ── Bước 3: Xoá chính comment này ──
        // DELETE FROM comments WHERE id = ?
        commentRepository.delete(comment);
    }

    // ════════════════════════════════════════════════════════════════
    // 7. UPDATE STATUS — Đổi trạng thái (RESOLVE / REOPEN)
    // ════════════════════════════════════════════════════════════════

    /**
     * Đổi trạng thái comment (ACTIVE ↔ RESOLVED).
     * Chỉ MANGAKA hoặc TANTOU_EDITOR mới có quyền này (@PreAuthorize).
     *
     * 📌 Quy trình:
     *    1. Tìm comment theo id
     *       - Nếu không tìm thấy → 404
     *    2. Cập nhật status
     *    3. Lưu lại
     *    4. Map + populate replies → trả về
     *
     * 📌 Luồng trạng thái (không giới hạn):
     *    ACTIVE  ←→  RESOLVED
     *    (Có thể chuyển qua lại tuỳ ý — không giới hạn chiều)
     *
     *    ACTIVE → RESOLVED:   Đã giải quyết xong (đánh dấu xanh)
     *    RESOLVED → ACTIVE:   Mở lại (cần chỉnh sửa thêm)
     *
     * 📌 Chỉ chuyển status cho COMMENT GỐC (parent = null):
     *    Reply không có status riêng — status của reply phụ thuộc vào
     *    comment gốC. (Quyết định này để tránh nhầm lẫn)
     *
     *    Tuy nhiên, hiện tại KHÔNG chặn chuyển status cho reply.
     *    Có thể bổ sung sau nếu cần.
     *
     * @param id     ID của comment cần đổi trạng thái
     * @param status Trạng thái mới (ACTIVE hoặc RESOLVED)
     * @param user   User đang đăng nhập (chỉ để log, không kiểm tra ở đây)
     * @return CommentResponse — comment sau khi đổi status
     * @throws AppException 404 — nếu không tìm thấy comment
     */
    public CommentResponse updateStatus(Long id, CommentStatus status, CustomUserDetails user) {
        // ── Bước 1: Tìm comment ──
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Comment not found with id: " + id));

        // ── Bước 2: Cập nhật status ──
        // Không kiểm tra hợp lệ — ACTIVE ↔ RESOLVED đều được cả 2 chiều
        comment.setStatus(status);

        // ── Bước 3: Lưu lại ──
        // UPDATE comments SET status = ?, updated_at = NOW() WHERE id = ?
        Comment savedComment = commentRepository.save(comment);

        // ── Bước 4: Map + populate replies → trả về ──
        return buildCommentTree(savedComment);
    }

    // ════════════════════════════════════════════════════════════════
    // PRIVATE HELPER — Xây dựng cây comment
    // ════════════════════════════════════════════════════════════════

    /**
     * Helper: Chuyển 1 Comment entity → CommentResponse + populate replies.
     *
     * 📌 Đây là method dùng chung cho tất cả các endpoint trả về comment:
     *    - getByPage:       xây cây cho từng comment gốc
     *    - getById:         xây cây cho 1 comment
     *    - create/reply:    xây cây sau khi tạo
     *    - update:          xây cây sau khi cập nhật
     *    - updateStatus:    xây cây sau khi đổi status
     *
     * 📌 Cách hoạt động:
     *    1. MapStruct chuyển entity → DTO (cơ bản)
     *    2. Tìm replies trong DB: WHERE parent_id = comment.id
     *    3. Map từng reply → DTO (gọi lại commentMapper.toResponse)
     *    4. Gán replies vào response
     *    5. Trả về response hoàn chỉnh
     *
     * 📌 Lưu ý:
     *    - Replies không populate replies của chúng (chỉ 1 cấp).
     *    - Tránh đệ quy vô hạn (reply → reply → reply → ...).
     *
     * @param comment Comment entity cần map
     * @return CommentResponse đã bao gồm replies
     */
    private CommentResponse buildCommentTree(Comment comment) {
        // ── Bước 1: Map entity → DTO cơ bản ──
        // commentMapper.toResponse() sẽ map:
        //   - author.id → authorId
        //   - author.displayName → authorName
        //   - author.avatarUrl → authorAvatar
        //   - parent.id → parentId
        //   - replies bỏ qua (null)
        CommentResponse response = commentMapper.toResponse(comment);

        // ── Bước 2: Populate replies ──
        // Tìm tất cả reply của comment này (WHERE parent_id = ?)
        List<Comment> replies = commentRepository.findByParentIdOrderByCreatedAtAsc(comment.getId());

        // Map từng reply entity → DTO (KHÔNG populate replies của reply — dừng 1 cấp)
        List<CommentResponse> replyDTOs = replies.stream()
                .map(commentMapper::toResponse)  // Chỉ map cơ bản, không đệ quy
                .toList();

        // ── Bước 3: Gán replies vào response ──
        response.setReplies(replyDTOs);

        // ── Bước 4: Trả về ──
        return response;
    }
}
