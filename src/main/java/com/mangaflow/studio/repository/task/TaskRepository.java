package com.mangaflow.studio.repository.task;

import com.mangaflow.studio.model.task.Task;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    /**
     * Lấy danh sách tasks có chứa 1 region cụ thể.
     * Dùng JOIN qua quan hệ @OneToMany (task.regions).
     */
    @EntityGraph(attributePaths = "submissions")
    @Query("SELECT t FROM Task t JOIN t.regions r WHERE r.id = :regionId ORDER BY t.assignedAt DESC")
    List<Task> findByRegionId(@Param("regionId") Long regionId);

    // ════════════════════════════════════════════════════════════
    // CÁC METHOD DÀNH CHO DASHBOARD
    // ════════════════════════════════════════════════════════════

    /**
     * Tìm tất cả task được giao cho 1 assistant.
     * Dùng trong DashboardAssistantStats: đếm task theo trạng thái.
     *
     * @param assistantId ID của assistant
     * @return danh sách task
     */
    List<Task> findByAssistantId(Long assistantId);

    /**
     * Tìm tất cả task do 1 người giao (assignedBy).
     * Dùng trong DashboardMangakaStats: đếm task pending, submitted.
     *
     * @param assignedById ID của người giao việc
     * @return danh sách task
     */
    List<Task> findByAssignedById(Long assignedById);
}
