package com.example.demo.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * ── User Entity ──
 * Ánh xạ tới bảng "users" trong database H2 (JPA tự động tạo bảng
 * dựa vào các annotation bên dưới).
 *
 * 📌 Các annotation quan trọng:
 *   @Entity         → Đánh dấu class này là 1 bảng trong DB
 *   @Table(name=..) → Đặt tên bảng (mặc định là tên class: "User")
 *   @Id             → Primary Key
 *   @GeneratedValue → Tự động tăng ID (IDENTITY = để DB tự sinh)
 *
 * 📌 Lombok:
 *   @Data     → Tự sinh getter, setter, toString, equals, hashCode
 *   @Builder  → Cho phép tạo object kiểu: User.builder().email(...).build()
 *   @NoArgsConstructor / @AllArgsConstructor → Constructor không tham số / có hết
 *
 * 📌 @PrePersist:
 *   Hibernate sẽ gọi phương thức này TRƯỚC KHI lưu entity lần đầu.
 *   Dùng để set các field mặc định (createdAt, updatedAt,...).
 *
 * 📌 @Column(unique = true):
 *   Đánh chỉ mục UNIQUE ở cấp DB, đảm bảo không trùng email/username.
 *   Có validation tồn tại trước (existsByEmail) ở Repository để trả
 *   về lỗi đẹp thay vì lỗi SQL.
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * email: dùng để đăng nhập.
     * nullable=false → NOT NULL trong DB
     * unique=true    → UNIQUE constraint
     */
    @Column(nullable = false, unique = true)
    private String email;

    /**
     * username: dùng để hiển thị và phân biệt người dùng.
     */
    @Column(nullable = false, unique = true)
    private String username;

    /**
     * password: LƯU Ý không bao giờ lưu plain text.
     * Phải mã hoá bằng BCryptPasswordEncoder trước khi lưu.
     * Ở bước AuthService sẽ dùng passwordEncoder.encode().
     */
    @Column(nullable = false)
    private String password;

    /**
     * displayName: Tên hiển thị (có thể khác username).
     * nullable=true (mặc định) → có thể null.
     */
    private String displayName;

    /**
     * @Enumerated(EnumType.STRING): Lưu tên enum dạng string.
     * Nếu không có annotation này, mặc định là ORDINAL (0,1,2...)
     * → dễ sai khi thêm/xoá enum ở giữa.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /**
     * avatarUrl: đường dẫn ảnh đại diện, có thể null.
     */
    private String avatarUrl;

    /**
     * bio: Giới thiệu ngắn về user.
     * @Column(length = 500): giới hạn độ dài 500 ký tự trong DB.
     * Có thể null.
     */
    @Column(length = 500)
    private String bio;

    /**
     * @Column(updatable = false): Không cho phép UPDATE cột này.
     * createdAt chỉ được set 1 lần khi tạo.
     */
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * @PrePersist: Lifecycle callback của JPA.
     * Phương thức này tự động chạy TRƯỚC khi entity được INSERT.
     * Dùng để set các giá trị mặc định mà không cần làm ở Service layer.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
