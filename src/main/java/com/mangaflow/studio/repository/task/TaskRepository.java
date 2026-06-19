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
}
