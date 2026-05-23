package com.example.demo.repository;

import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * ── UserRepository ──
 * Layer giao tiếp với database thông qua Spring Data JPA.
 *
 * 📌 JpaRepository<User, Long>:
 *   - User: entity cần quản lý
 *   - Long: kiểu dữ liệu của Primary Key (User.id)
 *
 * 📌 Spring Data JPA tự động sinh implementation cho các method
 *   dựa trên tên phương thức (query derivation):
 *
 *   findByEmail(String email)
 *     → "SELECT * FROM users WHERE email = ?"
 *     → Trả về Optional<User> (có thể empty nếu không tìm thấy)
 *
 *   existsByEmail(String email)
 *     → "SELECT COUNT(*) FROM users WHERE email = ?"
 *     → Trả về boolean (true nếu đã tồn tại)
 *
 *   existsByUsername(String username)
 *     → Tương tự với username
 *
 * 📌 Ngoài ra JpaRepository còn cung cấp sẵn:
 *   save(), findById(), findAll(), deleteById(), count(),...
 *   → Không cần viết code.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Tìm user theo email (dùng cho login).
     * Optional: có thể null → tránh NullPointerException.
     */
    Optional<User> findByEmail(String email);

    /**
     * Kiểm tra email đã tồn tại chưa (dùng cho register).
     */
    boolean existsByEmail(String email);

    /**
     * Kiểm tra username đã tồn tại chưa (dùng cho register).
     */
    boolean existsByUsername(String username);
}
