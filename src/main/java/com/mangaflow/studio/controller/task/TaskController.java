package com.mangaflow.studio.controller.task;

import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.task.request.AttachmentRequest;
import com.mangaflow.studio.dto.task.request.SubmissionStatusRequest;
import com.mangaflow.studio.dto.task.request.TaskRequest;
import com.mangaflow.studio.dto.task.request.TaskStatusRequest;
import com.mangaflow.studio.dto.task.request.TaskSubmissionRequest;
import com.mangaflow.studio.dto.task.request.TaskUpdateRequest;
import com.mangaflow.studio.dto.task.response.TaskAttachmentResponse;
import com.mangaflow.studio.dto.task.response.TaskResponse;
import com.mangaflow.studio.dto.task.response.TaskSubmissionResponse;
import com.mangaflow.studio.model.task.Priority;
import com.mangaflow.studio.model.task.TaskStatus;
import com.mangaflow.studio.model.task.TaskSubmissionStatus;
import com.mangaflow.studio.service.task.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ── TaskController ──
 * Controller xử lý tất cả API liên quan đến Task (công việc giao cho ASSISTANT).
 * Là tầng giao tiếp với frontend — nhận HTTP request, gọi Service, trả về response.
 * <p>
 * 📌 @RestController: Spring Bean — tự động serialize response thành JSON.
 * 📌 @RequestMapping("/api"): Base path — tất cả endpoint đều bắt đầu bằng /api.
 * 📌 @RequiredArgsConstructor: Lombok — DI constructor cho TaskService.
 * <p>
 * ══════════════════════════════════════════════════════════════════
 *  12 API endpoints:
 * ══════════════════════════════════════════════════════════════════
 *  ┌──────┬────────────────────────────────┬──────────────────────┬──────────────────┐
 *  │ End  │ Method │ Endpoint              │ Chức năng            │ Role             │
 *  ├──────┼────────┼────────────────────────┼──────────────────────┼──────────────────┤
 *  │ 1    │ GET    │ /api/tasks             │ Danh sách tasks     │ Authenticated    │
 *  │ 2    │ GET    │ /api/tasks/{id}        │ Chi tiết task       │ Authenticated    │
 *  │ 3    │ GET    │ /api/regions/{rid}/tasks │ Tasks của region │ Authenticated    │
 *  │ 4    │ POST   │ /api/regions/{rid}/tasks │ Tạo task mới     │ MANGAKA         │
 *  │ 5    │ PUT    │ /api/tasks/{id}        │ Cập nhật task       │ MANGAKA         │
 *  │ 6    │ PATCH  │ /api/tasks/{id}/status │ Đổi trạng thái      │ MANGAKA/ASSIST  │
 *  │ 7    │ DELETE │ /api/tasks/{id}        │ Xoá task            │ MANGAKA         │
 *  │ 8    │ GET    │ /api/tasks/{tid}/submissions │ Lịch sử nộp  │ Authenticated    │
 *  │ 9    │ POST   │ /api/tasks/{tid}/submissions │ Nộp bài      │ ASSISTANT        │
 *  │ 10   │ PATCH  │ /api/submissions/{id}/status │ Duyệt bài    │ MANGAKA         │
 *  │ 11   │ POST   │ /api/tasks/{tid}/attachments │ Đính kèm file│ MANGAKA         │
 *  │ 12   │ DELETE │ /api/attachments/{id}        │ Xoá file     │ MANGAKA         │
 *  └──────┴────────┴────────────────────────┴──────────────────────┴──────────────────┘
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "Quản lý công việc (Task) — giao việc, nộp bài, duyệt bài")
public class TaskController {

    /**
     * taskService: Service layer — chứa toàn bộ logic nghiệp vụ task.
     * Controller chỉ làm nhiệm vụ nhận request, gọi service, trả về response.
     */
    private final TaskService taskService;

    // ════════════════════════════════════════════════════════════════
    // 1. GET TASKS — Danh sách tasks (có filter + phân trang)
    // ════════════════════════════════════════════════════════════════

    /**
     * GET /api/tasks
     * <p>
     * Lấy danh sách tasks với filter động và phân trang.
     * Tuỳ role mà kết quả tự động filter khác nhau.
     * <p>
     * 📌 Query Parameters:
     *    - status, assignedTo, assignedBy, priority, regionId, seriesId
     *    - page (mặc định 0), size (mặc định 20, tối đa 100)
     */
    @Operation(summary = "Lấy danh sách tasks",
            description = "Trả về danh sách tasks với filter & phân trang. "
                    + "ASSISTANT tự động chỉ thấy task của mình. "
                    + "MANGAKA tự động chỉ thấy task mình giao.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Danh sách tasks (phân trang)"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @GetMapping("/tasks")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<TaskResponse>> getTasks(
            @Parameter(description = "Lọc theo trạng thái: TODO, IN_PROGRESS, DONE, REJECTED")
            @RequestParam(required = false) TaskStatus status,

            @Parameter(description = "Lọc theo ID của ASSISTANT được gán")
            @RequestParam(required = false) Long assignedTo,

            @Parameter(description = "Lọc theo ID của người giao việc")
            @RequestParam(required = false) Long assignedBy,

            @Parameter(description = "Lọc theo priority: LOW, MEDIUM, HIGH, URGENT")
            @RequestParam(required = false) Priority priority,

            @Parameter(description = "Lọc theo region ID")
            @RequestParam(required = false) Long regionId,

            @Parameter(description = "Lọc theo series ID (join region → page → chapter → series)")
            @RequestParam(required = false) Long seriesId,

            @Parameter(description = "Số trang (bắt đầu từ 0)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Số lượng mỗi trang (tối đa 100)")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Field sắp xếp: assignedAt, priority, dueDate, status,...")
            @RequestParam(defaultValue = "assignedAt") String sortBy,

            @Parameter(description = "Hướng sắp xếp: asc hoặc desc")
            @RequestParam(defaultValue = "desc") String sortDir,

            @AuthenticationPrincipal CustomUserDetails user) {

        if (size > 100) size = 100;

        Page<TaskResponse> result = taskService.getTasks(
                status, assignedTo, assignedBy, priority, regionId, seriesId,
                page, size, sortBy, sortDir, user);

        return ResponseEntity.ok(result);
    }

    // ════════════════════════════════════════════════════════════════
    // 2. GET TASK BY ID — Chi tiết 1 task
    // ════════════════════════════════════════════════════════════════

    /**
     * GET /api/tasks/{id}
     * <p>
     * Lấy chi tiết 1 task kèm submissions (lịch sử nộp bài) và attachments.
     */
    @Operation(summary = "Chi tiết 1 task",
            description = "Trả về chi tiết task kèm danh sách submissions & attachments.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chi tiết task"),
            @ApiResponse(responseCode = "404", description = "Task không tồn tại")
    })
    @GetMapping("/tasks/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TaskResponse> getTaskById(
            @Parameter(description = "ID của task", example = "1")
            @PathVariable Long id) {

        return ResponseEntity.ok(taskService.getTaskById(id));
    }

    // ════════════════════════════════════════════════════════════════
    // 3. GET TASKS BY REGION — Tasks của 1 region
    // ════════════════════════════════════════════════════════════════

    /**
     * GET /api/regions/{regionId}/tasks
     * <p>
     * Lấy tất cả tasks của 1 region (không kèm submissions/attachments).
     */
    @Operation(summary = "Tasks của 1 region",
            description = "Lấy tất cả tasks thuộc 1 region. "
                    + "ASSISTANT chỉ thấy task của mình. "
                    + "Không kèm submissions & attachments để giảm tải.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Danh sách tasks"),
            @ApiResponse(responseCode = "404", description = "Region không tồn tại")
    })
    @GetMapping("/regions/{regionId}/tasks")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TaskResponse>> getTasksByRegion(
            @Parameter(description = "ID của region", example = "10")
            @PathVariable Long regionId,

            @AuthenticationPrincipal CustomUserDetails user) {

        return ResponseEntity.ok(taskService.getTasksByRegion(regionId, user));
    }

    // ════════════════════════════════════════════════════════════════
    // 4. CREATE TASK — Tạo task mới
    // ════════════════════════════════════════════════════════════════

    /**
     * POST /api/regions/{regionId}/tasks
     * <p>
     * MANGAKA tạo task mới và gán cho 1 ASSISTANT.
     * Tự động copy pageImageUrl từ region → page.
     */
    @Operation(summary = "Tạo task mới",
            description = "MANGAKA tạo task mới gắn với 1 region và gán cho ASSISTANT. "
                    + "Hệ thống tự động copy pageImageUrl từ region sang task.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Task đã tạo"),
            @ApiResponse(responseCode = "400", description = "Assistant không có role ASSISTANT, hoặc dueDate ở quá khứ"),
            @ApiResponse(responseCode = "404", description = "Region hoặc user không tồn tại")
    })
    @PostMapping("/regions/{regionId}/tasks")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<TaskResponse> createTask(
            @Parameter(description = "ID của region cần giao việc", example = "10")
            @PathVariable Long regionId,

            @Parameter(description = "Thông tin task cần tạo")
            @Valid @RequestBody TaskRequest request,

            @AuthenticationPrincipal CustomUserDetails user) {

        TaskResponse response = taskService.createTask(regionId, request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ════════════════════════════════════════════════════════════════
    // 5. UPDATE TASK — Cập nhật task
    // ════════════════════════════════════════════════════════════════

    /**
     * PUT /api/tasks/{id}
     * <p>
     * Cập nhật thông tin task. Chỉ sửa được khi TODO hoặc REJECTED.
     * Nếu đổi assistantId → reset assignedAt.
     */
    @Operation(summary = "Cập nhật task",
            description = "Cập nhật thông tin task. Chỉ sửa được khi TODO hoặc REJECTED. "
                    + "Nếu đổi assistantId → reset assignedAt.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task đã cập nhật"),
            @ApiResponse(responseCode = "400", description = "Task không ở TODO/REJECTED hoặc dueDate ở quá khứ"),
            @ApiResponse(responseCode = "404", description = "Task không tồn tại")
    })
    @PutMapping("/tasks/{id}")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<TaskResponse> updateTask(
            @Parameter(description = "ID của task", example = "1")
            @PathVariable Long id,

            @Parameter(description = "Thông tin task cần cập nhật (field null = giữ nguyên)")
            @RequestBody TaskUpdateRequest request,

            @AuthenticationPrincipal CustomUserDetails user) {

        return ResponseEntity.ok(taskService.updateTask(id, request, user));
    }

    // ════════════════════════════════════════════════════════════════
    // 6. UPDATE TASK STATUS — Đổi trạng thái task
    // ════════════════════════════════════════════════════════════════

    /**
     * PATCH /api/tasks/{id}/status
     * <p>
     * Chuyển trạng thái task. ASSISTANT nhận task, MANGAKA từ chối.
     * <p>
     * Không hỗ trợ IN_PROGRESS → DONE (dùng submission + review).
     */
    @Operation(summary = "Đổi trạng thái task",
            description = "Chuyển trạng thái task. "
                    + "TODO → IN_PROGRESS (ASSISTANT), "
                    + "IN_PROGRESS → REJECTED (MANGAKA), "
                    + "REJECTED → IN_PROGRESS (ASSISTANT). "
                    + "(Không hỗ trợ IN_PROGRESS → DONE — dùng submission + review)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task đã đổi trạng thái"),
            @ApiResponse(responseCode = "400", description = "Chuyển trạng thái không hợp lệ"),
            @ApiResponse(responseCode = "403", description = "Không có quyền thực hiện chuyển này"),
            @ApiResponse(responseCode = "404", description = "Task không tồn tại")
    })
    @PatchMapping("/tasks/{id}/status")
    @PreAuthorize("hasAnyRole('MANGAKA', 'ASSISTANT')")
    public ResponseEntity<TaskResponse> updateTaskStatus(
            @Parameter(description = "ID của task", example = "1")
            @PathVariable Long id,

            @Parameter(description = "Trạng thái mới")
            @Valid @RequestBody TaskStatusRequest request,

            @AuthenticationPrincipal CustomUserDetails user) {

        return ResponseEntity.ok(
                taskService.updateTaskStatus(id, request.getStatus(), user));
    }

    // ════════════════════════════════════════════════════════════════
    // 7. DELETE TASK — Xoá task
    // ════════════════════════════════════════════════════════════════

    /**
     * DELETE /api/tasks/{id}
     * <p>
     * Xoá task. Chỉ xoá được khi TODO (chưa ai nhận).
     */
    @Operation(summary = "Xoá task",
            description = "Xoá task. Chỉ xoá được khi task đang TODO "
                    + "(chưa ai nhận làm). Cascade xoá luôn submissions & attachments.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã xoá thành công"),
            @ApiResponse(responseCode = "400", description = "Task không ở TODO — không thể xoá"),
            @ApiResponse(responseCode = "404", description = "Task không tồn tại")
    })
    @DeleteMapping("/tasks/{id}")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<Void> deleteTask(
            @Parameter(description = "ID của task cần xoá", example = "1")
            @PathVariable Long id) {

        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }

    // ════════════════════════════════════════════════════════════════
    // 8. GET SUBMISSIONS — Lịch sử nộp bài
    // ════════════════════════════════════════════════════════════════

    /**
     * GET /api/tasks/{taskId}/submissions
     * <p>
     * Lấy lịch sử các lần nộp bài của 1 task.
     * Sắp xếp theo version giảm dần (mới nhất trước).
     */
    @Operation(summary = "Lịch sử nộp bài",
            description = "Lấy lịch sử các lần nộp bài của 1 task, "
                    + "sắp xếp theo version giảm dần (mới nhất trước).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Danh sách submissions"),
            @ApiResponse(responseCode = "404", description = "Task không tồn tại")
    })
    @GetMapping("/tasks/{taskId}/submissions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TaskSubmissionResponse>> getSubmissions(
            @Parameter(description = "ID của task", example = "1")
            @PathVariable Long taskId) {

        return ResponseEntity.ok(taskService.getSubmissions(taskId));
    }

    // ════════════════════════════════════════════════════════════════
    // 9. SUBMIT TASK — ASSISTANT nộp bài
    // ════════════════════════════════════════════════════════════════

    /**
     * POST /api/tasks/{taskId}/submissions
     * <p>
     * ASSISTANT nộp bài làm. Tự động tăng version.
     * Chỉ nộp được khi task IN_PROGRESS hoặc REJECTED.
     */
    @Operation(summary = "Nộp bài làm",
            description = "ASSISTANT nộp bài làm cho task. "
                    + "Hệ thống tự động tăng version (version cao nhất + 1). "
                    + "Chỉ nộp được khi task IN_PROGRESS hoặc REJECTED.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Bài làm đã nộp"),
            @ApiResponse(responseCode = "400", description = "Task không ở IN_PROGRESS hoặc REJECTED"),
            @ApiResponse(responseCode = "403", description = "Bạn không phải ASSISTANT được gán"),
            @ApiResponse(responseCode = "404", description = "Task không tồn tại")
    })
    @PostMapping("/tasks/{taskId}/submissions")
    @PreAuthorize("hasRole('ASSISTANT')")
    public ResponseEntity<TaskSubmissionResponse> submitTask(
            @Parameter(description = "ID của task cần nộp bài", example = "1")
            @PathVariable Long taskId,

            @Parameter(description = "Thông tin bài nộp")
            @Valid @RequestBody TaskSubmissionRequest request,

            @AuthenticationPrincipal CustomUserDetails user) {

        TaskSubmissionResponse response = taskService.submitTask(taskId, request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ════════════════════════════════════════════════════════════════
    // 10. REVIEW SUBMISSION — MANGAKA duyệt bài
    // ════════════════════════════════════════════════════════════════

    /**
     * PATCH /api/submissions/{id}/status
     * <p>
     * MANGAKA duyệt bài nộp.
     * APPROVED → task DONE. REVISION_REQUIRED → task IN_PROGRESS.
     */
    @Operation(summary = "Duyệt bài nộp",
            description = "MANGAKA duyệt bài nộp của ASSISTANT. "
                    + "APPROVED → task đánh dấu DONE. "
                    + "REVISION_REQUIRED → task quay về IN_PROGRESS để ASSISTANT sửa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bài nộp đã duyệt"),
            @ApiResponse(responseCode = "400", description = "Submission không ở SUBMITTED hoặc status không hợp lệ"),
            @ApiResponse(responseCode = "403", description = "Bạn không phải MANGAKA tạo task này"),
            @ApiResponse(responseCode = "404", description = "Submission không tồn tại")
    })
    @PatchMapping("/submissions/{id}/status")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<TaskSubmissionResponse> reviewSubmission(
            @Parameter(description = "ID của submission cần duyệt", example = "1")
            @PathVariable Long id,

            @Parameter(description = "Trạng thái duyệt: APPROVED hoặc REVISION_REQUIRED")
            @Valid @RequestBody SubmissionStatusRequest request,

            @AuthenticationPrincipal CustomUserDetails user) {

        return ResponseEntity.ok(
                taskService.reviewSubmission(id, request.getStatus(), user));
    }

    // ════════════════════════════════════════════════════════════════
    // 11. ADD ATTACHMENT — Đính kèm file tham khảo
    // ════════════════════════════════════════════════════════════════

    /**
     * POST /api/tasks/{taskId}/attachments
     * <p>
     * MANGAKA đính kèm file tham khảo cho task.
     */
    @Operation(summary = "Đính kèm file tham khảo",
            description = "MANGAKA đính kèm file tham khảo (ảnh mẫu, tài liệu) cho task.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "File đã đính kèm"),
            @ApiResponse(responseCode = "403", description = "Bạn không phải MANGAKA tạo task này"),
            @ApiResponse(responseCode = "404", description = "Task không tồn tại")
    })
    @PostMapping("/tasks/{taskId}/attachments")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<TaskAttachmentResponse> addAttachment(
            @Parameter(description = "ID của task", example = "1")
            @PathVariable Long taskId,

            @Parameter(description = "URL file đính kèm")
            @Valid @RequestBody AttachmentRequest request,

            @AuthenticationPrincipal CustomUserDetails user) {

        TaskAttachmentResponse response = taskService.addAttachment(taskId, request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ════════════════════════════════════════════════════════════════
    // 12. DELETE ATTACHMENT — Xoá file đính kèm
    // ════════════════════════════════════════════════════════════════

    /**
     * DELETE /api/attachments/{id}
     * <p>
     * Xoá file đính kèm khỏi task.
     */
    @Operation(summary = "Xoá file đính kèm",
            description = "Xoá file đính kèm khỏi task.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã xoá thành công"),
            @ApiResponse(responseCode = "404", description = "Attachment không tồn tại")
    })
    @DeleteMapping("/attachments/{id}")
    @PreAuthorize("hasRole('MANGAKA')")
    public ResponseEntity<Void> deleteAttachment(
            @Parameter(description = "ID của attachment cần xoá", example = "1")
            @PathVariable Long id) {

        taskService.deleteAttachment(id);
        return ResponseEntity.noContent().build();
    }
}
