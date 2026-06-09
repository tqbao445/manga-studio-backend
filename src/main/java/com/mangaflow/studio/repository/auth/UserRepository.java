package com.mangaflow.studio.repository.auth;

import com.mangaflow.studio.model.auth.Role;
import com.mangaflow.studio.model.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    /**
     * Tìm tất cả user có role cụ thể.
     * <p>
     * Dùng trong:
     *   - UserController.getAssistants(): lấy danh sách ASSISTANT để frontend hiển thị dropdown
     *   - SeriesAssistantService.inviteAssistant(): kiểm tra user có role ASSISTANT không
     *   - (Legacy: SeriesWorkflowService.approve() — đã xoá, dùng tantouApprove thay thế)
     * <p>
     * 📌 Spring Data JPA tự động sinh query: WHERE role = ?
     *
     * @param role role cần lọc (MANGAKA, ASSISTANT, TANTOU_EDITOR, EDITORIAL_BOARD)
     * @return danh sách user có role đó (có thể rỗng)
     */
    List<User> findByRole(Role role);

    /**
     * Tìm user theo role và search theo displayName hoặc username (không phân biệt hoa thường).
     * <p>
     * Dùng trong:
     *   - UserController.getAssistants(?search=...): frontend gõ tìm assistant theo tên
     * <p>
     * 📌 @Query: JPQL query với LIKE %search% trên cả displayName và username.
     *    Nếu search rỗng → trả về tất cả user có role đó.
     * <p>
     * 📌 COALESCE: displayName có thể NULL → thay bằng username để vẫn tìm được.
     *
     * @param role   role cần lọc
     * @param search từ khoá tìm kiếm (LIKE %search%)
     * @return danh sách user khớp (có thể rỗng)
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.role = :role
              AND (:search IS NULL
                   OR LOWER(COALESCE(u.displayName, u.username)) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY u.displayName, u.username
            """)
    List<User> findByRoleAndSearch(@Param("role") Role role, @Param("search") String search);
}
