package com.mangaflow.studio.model.auth;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * ── VoteCriterion ──
 * Tiêu chí chấm điểm cho việc phê duyệt series.
 *
 * Tại sao cần bảng này?
 * - Editorial Board không chỉ vote YES/NO mà còn chấm điểm
 *   theo nhiều tiêu chí: Nội dung, Chất lượng vẽ, Sáng tạo,...
 * - Admin có thể thêm/bớt tiêu chí, thay đổi trọng số.
 * - is_active cho phép bật/tắt tiêu chí mà không cần xoá.
 */
@Entity
@Table(name = "vote_criterion")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoteCriterion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tên tiêu chí — hiển thị trên form vote.
     * VD: "Nội dung kịch bản", "Chất lượng nét vẽ", "Sáng tạo"
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Giải thích tiêu chí này dùng để làm gì.
     * VD: "Đánh giá cốt truyện có lôi cuốn, nhân vật có chiều sâu không"
     */
    @Column(length = 500)
    private String description;

    /**
     * Trọng số của tiêu chí (1-5).
     * Tiêu chí nào quan trọng thì weight cao hơn.
     * Ảnh hưởng đến cách tính điểm tổng kết.
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer weight = 1;

    /**
     * Thứ tự hiển thị trên giao diện vote.
     * Sort_order càng nhỏ càng hiển thị trước.
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    /**
     * Bật/tắt tiêu chí.
     * Nếu is_active = false thì không hiện trên form vote.
     * Giúp admin linh hoạt thay đổi tiêu chí theo từng kỳ.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
