package com.mangaflow.studio.model.comment;

import com.mangaflow.studio.model.auth.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * ── Comment Entity ──
 * Ánh xạ tới bảng "comments" trong database.
 * Mỗi Comment là 1 lời nhắn / đánh dấu trên 1 page truyện.
 *
 * 📌 @Entity: Đánh dấu class này là 1 JPA entity → Hibernate tự động
 *    tạo bảng "comments" trong database với các cột tương ứng field.
 *
 * 📌 @Table(name = "comments"): Tên bảng trong DB.
 *
 * 📌 @Data (Lombok): Tự sinh getter, setter, toString, equals, hashCode.
 * 📌 @NoArgsConstructor: JPA cần constructor không tham số.
 * 📌 @AllArgsConstructor: Tiện cho việc tạo object.
 * 📌 @Builder: Pattern cho phép tạo object kiểu:
 *    Comment.builder().content("...").pageId(1L).build()
 *
 * ══════════════════════════════════════════════════════════════════
 *  Cách hiểu:
 * ══════════════════════════════════════════════════════════════════
 *
 *  📌 Comment gốC (parent = null):
 *     - Là 1 annotation trên page — có toạ độ (posX, posY).
 *     - Frontend vẽ 1 ô/vòng tròn màu lên ảnh page tại vị trí đó.
 *     - Có thể resolve (đánh dấu đã xử lý xong).
 *
 *  📌 Reply (parent = id của comment gốc):
 *     - Là câu trả lời trong thread của 1 comment gốc.
 *     - KHÔNG có toạ độ → không vẽ ô riêng trên ảnh.
 *     - Hiển thị dạng nested text bên dưới comment gốc.
 *
 * ══════════════════════════════════════════════════════════════════
 *  Ví dụ trong DB:
 * ══════════════════════════════════════════════════════════════════
 *  id=1, content="Thoại sai dòng 3", parent_id=NULL, page_id=10, pos_x=150, pos_y=200
 *    → Comment gốc: "Thoại sai dòng 3", vẽ ô đỏ tại (150,200) trên ảnh page 10
 *
 *  id=2, content="Đã sửa", parent_id=1, page_id=10, pos_x=NULL, pos_y=NULL
 *    → Reply: "Đã sửa" — ẩn dưới comment id=1
 *
 *  id=3, content="Check lại nhé", parent_id=1, page_id=10
 *    → Reply tiếp theo trong thread của comment id=1
 */
@Entity
@Table(name = "comments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment {

    // ════════════════════════════════════════════════════════════════
    // Khoá chính
    // ════════════════════════════════════════════════════════════════

    /**
     * id: Khoá chính, tự động tăng (IDENTITY = SQL Server tự sinh).
     * Mỗi comment có 1 id duy nhất.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ════════════════════════════════════════════════════════════════
    // Nội dung comment
    // ════════════════════════════════════════════════════════════════

    /**
     * content: Nội dung text của comment.
     * NOT NULL — bắt buộc phải có chữ.
     * columnDefinition = "NVARCHAR(MAX)" — hỗ trợ tiếng Việt, không giới hạn độ dài.
     */
    @Column(nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String content;

    // ════════════════════════════════════════════════════════════════
    // Người viết (Many-to-One → User)
    // ════════════════════════════════════════════════════════════════

    /**
     * author: Người viết comment.
     * ManyToOne + LAZY: chỉ load entity User khi cần.
     * JoinColumn author_id: khoá ngoại tới bảng users.
     * NOT NULL — mỗi comment phải có tác giả.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    // ════════════════════════════════════════════════════════════════
    // Self-reference (thread reply)
    // ════════════════════════════════════════════════════════════════

    /**
     * parent: Comment cha — self-reference.
     * ManyToOne: nhiều reply cùng trỏ về 1 comment gốc.
     * LAZY: chỉ load khi cần.
     * NULLABLE: null = comment gốc, có giá trị = reply.
     *
     * 📌 Cấu trúc:
     *    Comment gốc:  parent = null
     *    Reply:        parent = comment_gốc
     *
     * 📌 Giới hạn: Chỉ 1 cấp — reply không thể reply tiếp.
     *    (Tránh đệ quy sâu khi load dữ liệu)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    // ════════════════════════════════════════════════════════════════
    // Page chứa comment
    // ════════════════════════════════════════════════════════════════

    /**
     * pageId: ID của page chứa comment.
     * Dùng Long thay vì @ManyToOne — đơn giản, tránh N+1 query.
     * NOT NULL — comment nào cũng phải thuộc 1 page.
     *
     * 🎯 Khi cần entity Page: dùng PageRepository.findById(pageId).
     */
    @Column(name = "page_id", nullable = false)
    private Long pageId;

    // ════════════════════════════════════════════════════════════════
    // Trạng thái
    // ════════════════════════════════════════════════════════════════

    /**
     * status: Trạng thái của comment.
     * Mặc định: ACTIVE (vừa tạo, cần xử lý).
     * @Enumerated(EnumType.STRING): lưu chữ "ACTIVE" hoặc "RESOLVED".
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CommentStatus status = CommentStatus.ACTIVE;

    // ════════════════════════════════════════════════════════════════
    // Toạ độ annotation (dành cho comment gốc)
    // ════════════════════════════════════════════════════════════════

    /**
     * posX: Toạ độ X trên ảnh page (pixel).
     * NULLABLE: reply không có toạ độ.
     * Tính từ góc trên-bên-trái của ảnh page.
     */
    @Column(name = "pos_x")
    private Double posX;

    /**
     * posY: Toạ độ Y trên ảnh page (pixel).
     */
    @Column(name = "pos_y")
    private Double posY;

    /**
     * posWidth: Chiều rộng vùng đánh dấu (pixel).
     * Dùng để vẽ ô/hình tròn trên ảnh.
     */
    @Column(name = "pos_width")
    private Double posWidth;

    /**
     * posHeight: Chiều cao vùng đánh dấu (pixel).
     */
    @Column(name = "pos_height")
    private Double posHeight;

    // ════════════════════════════════════════════════════════════════
    // Timestamps (tự động quản lý)
    // ════════════════════════════════════════════════════════════════

    /**
     * createdAt: Thời điểm tạo comment.
     * @Column(updatable = false): không cho UPDATE sau khi insert.
     * Set tự động trong @PrePersist.
     */
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * updatedAt: Thời điểm cập nhật gần nhất.
     * Set tự động trong @PrePersist và @PreUpdate.
     */
    private LocalDateTime updatedAt;

    // ════════════════════════════════════════════════════════════════
    // Lifecycle callbacks
    // ════════════════════════════════════════════════════════════════

    /**
     * ── @PrePersist ──
     * JPA lifecycle callback — tự động chạy TRƯỚC KHI insert entity.
     * Set createdAt + updatedAt lần đầu.
     * Không cần gọi thủ công ở Service layer.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * ── @PreUpdate ──
     * JPA lifecycle callback — tự động chạy TRƯỚC KHI update entity.
     * Chỉ set updatedAt (createdAt giữ nguyên).
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
