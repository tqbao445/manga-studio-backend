package com.mangaflow.studio.model.page;

import com.mangaflow.studio.model.chapter.Chapter;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * ── Page Entity ──
 * Ánh xạ tới bảng "pages" trong database.
 * Mỗi Page là 1 trang truyện (file ảnh) thuộc về 1 chapter.
 *
 * 📌 @Entity: Đánh dấu class này là 1 JPA entity -> Hibernate tự động
 *    tạo bảng "pages" trong database với các cột tương ứng field.
 *
 * 📌 @Table:
 *    - name = "pages" -> tên bảng trong DB
 *    - uniqueConstraints -> ràng buộc: 1 chapter không có 2 page trùng số
 *      (chapterId + pageNumber là duy nhất)
 *
 * 📌 @Data (Lombok): Tự sinh getter, setter, toString, equals, hashCode
 * 📌 @NoArgsConstructor: JPA cần constructor không tham số
 * 📌 @AllArgsConstructor: Tiện cho việc tạo object
 * 📌 @Builder: Pattern cho phép tạo object kiểu: Page.builder().title(...).build()
 *
 * ══════════════════════════════════════════════════════════════════
 *  Lưu ý về chapterId vs chapter:
 * ══════════════════════════════════════════════════════════════════
 *  Page có 2 cách truy cập chapter:
 *   - chapterId (Long): dùng trong query, setter, builder — KHÔNG đổi
 *   - chapter (@ManyToOne LAZY): load entity Chapter khi cần
 *
 *  📌 insertable = false, updatable = false trên @ManyToOne:
 *     JPA chỉ dùng chapterId để INSERT/UPDATE, chapter chỉ để đọc.
 *     Tránh xung đột — code cũ vẫn dùng chapterId.setter() thoải mái.
 */
@Entity
@Table(name = "pages", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"chapterId", "pageNumber"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Page {

    /**
     * id: Khoá chính, tự động tăng (IDENTITY = SQL Server tự sinh).
     * Mỗi page có 1 id duy nhất.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * chapterId: ID của chapter chứa page này.
     * NOT NULL — mỗi page phải thuộc về 1 chapter.
     * <p>
     * 📌 Dùng trong INSERT/UPDATE và các query (findByChapterId...).
     *    Code cũ set chapterId = value vẫn hoạt động bình thường.
     */
    @Column(name = "chapter_id", nullable = false)
    private Long chapterId;

    /**
     * chapter: Entity Chapter (LAZY — chỉ load khi cần).
     * <p>
     * 📌 insertable = false, updatable = false:
     *    JPA dùng chapterId để ghi DB, chapter chỉ để đọc.
     *    Cho phép gọi page.getChapter().getSeriesId() trực tiếp.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id", nullable = false, insertable = false, updatable = false)
    private Chapter chapter;

    /**
     * pageNumber: Số thứ tự của page trong chapter.
     * NOT NULL — page nào cũng có số thứ tự.
     *
     * 📕 Dùng để sắp xếp thứ tự pages.
     *    Khi kéo thả đổi vị trí, chỉ cần thay đổi số này.
     *    Ràng buộc UNIQUE(chapterId, pageNumber) đảm bảo
     *    không có 2 page trùng số trong cùng 1 chapter.
     */
    @Column(nullable = false)
    private Integer pageNumber;

    /**
     * originalImageUrl: URL ảnh gốc (full size) trên Cloudinary.
     * NOT NULL — bắt buộc phải có.
     *
     * 📌 Dùng khi mở page trong workspace để vẽ region.
     *    Ảnh này có kích thước đầy đủ (vd: 4200x6000px).
     */
    @Column(nullable = false, length = 500)
    private String originalImageUrl;

    /**
     * webImageUrl: URL ảnh đã resize cho web (width 1920px).
     * NOT NULL — bắt buộc phải có.
     *
     * 📌 Dùng để hiển thị page trên trình duyệt.
     *    Nhỏ hơn ảnh gốc -> load nhanh hơn.
     *    Cloudinary tự động resize từ ảnh gốc, không tốn dung lượng.
     */
    @Column(nullable = false, length = 500)
    private String webImageUrl;

    /**
     * thumbnailUrl: URL ảnh thumbnail (width 320px).
     * NULLABLE — có thể null nếu chưa tạo thumbnail.
     *
     * 📌 Dùng trong danh sách pages (ChapterDetailPage).
     *    Ảnh nhỏ -> load rất nhanh.
     */
    @Column(length = 500)
    private String thumbnailUrl;

    /**
     * publicId: ID của ảnh trên Cloudinary (không bao gồm version, format).
     * Dùng để xoá ảnh khỏi Cloudinary khi xoá page.
     *
     * 📌 Ví dụ: "manga_studio/u3/s1/ch5/p1"
     *
     * 📌 Không lưu URL đầy đủ vì:
     *    - URL có thể thay đổi (version, transform)
     *    - publicId là định danh duy nhất, không đổi
     *    - Xoá ảnh cần publicId, không cần URL
     */
    @Column(nullable = false, length = 500)
    private String publicId;

    /**
     * width: Chiều rộng của ảnh gốc (px).
     * NOT NULL — lấy từ Cloudinary sau khi upload.
     *
     * Frontend cần thông tin này để tính toán hiển thị.
     */
    @Column(nullable = false)
    private Integer width;

    /**
     * height: Chiều cao của ảnh gốc (px).
     * NOT NULL — lấy từ Cloudinary sau khi upload.
     */
    @Column(nullable = false)
    private Integer height;

    /**
     * finalImageUrl: URL ảnh sau khi merge tất cả layers.
     * NULLABLE — chỉ có giá trị sau khi MANGAKA merge lần đầu.
     * Khi merge lại → ghi đè URL mới (overwrite trên Cloudinary).
     * <p>
     * 📕 Dùng trong workspace để xem/xuất ảnh hoàn chỉnh.
     *    Frontend hiển thị nút "Merge & Export" → gọi API merge.
     *    Cloudinary folder: .../p{pageNumber}/merge/final.jpg
     */
    @Column(name = "final_image_url", length = 500)
    private String finalImageUrl;

    /**
     * status: Trạng thái hiện tại của page.
     * Mặc định: UPLOADED (vừa upload xong).
     *
     * @Enumerated(EnumType.STRING)
     *   → Lưu tên enum dạng chữ (VD: "UPLOADED"), không phải số (0,1,2...)
     *   → Dễ đọc trong database và dễ debug.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PageStatus status = PageStatus.UPLOADED;

    /**
     * createdAt: Thời điểm tạo page.
     * @Column(updatable = false) -> không cho UPDATE sau khi insert.
     * Set tự động trong @PrePersist.
     */
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * updatedAt: Thời điểm cập nhật gần nhất.
     * Set tự động trong @PrePersist và @PreUpdate.
     */
    private LocalDateTime updatedAt;

    /**
     * ── @PrePersist ──
     * JPA lifecycle callback — tự động chạy TRƯỚC KHI insert entity.
     * Set createdAt + updatedAt lần đầu.
     *
     * 📌 Không cần gọi thủ công ở Service layer.
     *    Hibernate tự gọi method này.
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
