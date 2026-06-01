package com.mangaflow.studio.service.task;

import com.mangaflow.studio.common.exception.AppException;
import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.task.mapper.TaskAttachmentMapper;
import com.mangaflow.studio.dto.task.mapper.TaskMapper;
import com.mangaflow.studio.dto.task.mapper.TaskSubmissionMapper;
import com.mangaflow.studio.dto.task.request.AttachmentRequest;
import com.mangaflow.studio.dto.task.request.TaskRequest;
import com.mangaflow.studio.dto.task.request.TaskSubmissionRequest;
import com.mangaflow.studio.dto.task.request.TaskUpdateRequest;
import com.mangaflow.studio.dto.task.response.TaskAttachmentResponse;
import com.mangaflow.studio.dto.task.response.TaskResponse;
import com.mangaflow.studio.dto.task.response.TaskSubmissionResponse;
import com.mangaflow.studio.model.auth.Role;
import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.model.region.Region;
import com.mangaflow.studio.model.task.*;
import com.mangaflow.studio.repository.auth.UserRepository;
import com.mangaflow.studio.model.chapter.Chapter;
import com.mangaflow.studio.repository.chapter.ChapterRepository;
import com.mangaflow.studio.repository.page.PageRepository;
import com.mangaflow.studio.repository.region.RegionRepository;
import com.mangaflow.studio.repository.task.TaskAttachmentRepository;
import com.mangaflow.studio.repository.task.TaskRepository;
import com.mangaflow.studio.repository.task.TaskSubmissionRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ── TaskService ──
 * Service xử lý toàn bộ logic nghiệp vụ liên quan đến Task (công việc).
 * Là tầng trung gian giữa TaskController (API) và các Repository (DB).
 * <p>
 * 📌 @Service: Spring Bean — chứa business logic, quản lý transaction.
 * 📌 @RequiredArgsConstructor: Lombok — DI constructor cho tất cả field final.
 * 📌 @Transactional: Tất cả method trong class đều chạy trong transaction.
 * <p>
 * ══════════════════════════════════════════════════════════════════
 *  Danh sách method (tương ứng 12 endpoints):
 * ══════════════════════════════════════════════════════════════════
 *  1.  getTasks()              — GET    /api/tasks
 *  2.  getTaskById()           — GET    /api/tasks/{id}
 *  3.  getTasksByRegion()      — GET    /api/regions/{regionId}/tasks
 *  4.  createTask()            — POST   /api/regions/{regionId}/tasks
 *  5.  updateTask()            — PUT    /api/tasks/{id}
 *  6.  updateTaskStatus()      — PATCH  /api/tasks/{id}/status
 *  7.  deleteTask()            — DELETE /api/tasks/{id}
 *  8.  getSubmissions()        — GET    /api/tasks/{taskId}/submissions
 *  9.  submitTask()            — POST   /api/tasks/{taskId}/submissions
 *  10. reviewSubmission()      — PATCH  /api/submissions/{id}/status
 *  11. addAttachment()         — POST   /api/tasks/{taskId}/attachments
 *  12. deleteAttachment()      — DELETE /api/attachments/{id}
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TaskService {

    // ════════════════════════════════════════════════════════════════
    // DI — Các dependency được inject qua constructor
    // ════════════════════════════════════════════════════════════════

    private final TaskRepository taskRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final RegionRepository regionRepository;
    private final UserRepository userRepository;
    private final PageRepository pageRepository;

    private final ChapterRepository chapterRepository;

    private final TaskMapper taskMapper;
    private final TaskSubmissionMapper taskSubmissionMapper;
    private final TaskAttachmentMapper taskAttachmentMapper;

    // ════════════════════════════════════════════════════════════════
    // 1. GET TASKS — Danh sách tasks (có filter + phân trang)
    // ════════════════════════════════════════════════════════════════

    /**
     * GET /api/tasks
     * <p>
     * Lấy danh sách tasks với filter động và phân trang.
     * Tuỳ role mà kết quả trả về khác nhau:
     * - ASSISTANT:      chỉ thấy task của mình (assignedTo = currentUserId)
     * - MANGAKA:       chỉ thấy task mình giao (assignedBy = currentUserId)
     * - TANTOU_EDITOR / EDITORIAL_BOARD: thấy tất cả task
     * <p>
     * 📌 Dùng JPA Specification để build WHERE clause động:
     *    - status, assignedTo, assignedBy, priority, regionId, seriesId
     *    - role-based filter tự động (ASSISTANT/MANGAKA)
     * <p>
     * 📌 seriesId filter:
     *    Task → Region (regionId) → Page (pageId) → Chapter (chapterId) → Series
     *    Vì Region không có @ManyToOne với Page, cần query qua pageId.
     *    → Dùng subquery qua PageRepository để tìm regionIds của series.
     *
     * @param status     Lọc theo trạng thái (optional)
     * @param assignedTo Lọc theo ASSISTANT ID (optional)
     * @param assignedBy Lọc theo MANGAKA ID (optional)
     * @param priority   Lọc theo mức ưu tiên (optional)
     * @param regionId   Lọc theo region (optional)
     * @param seriesId   Lọc theo series (optional) — join qua region → page → chapter
     * @param pageable   Thông tin phân trang (page, size, sort)
     * @param user       User hiện tại (từ JWT) — cho role-based auto filter
     * @return Page<TaskResponse> trang kết quả (không kèm submissions/attachments)
     */
    @Transactional(readOnly = true)
    public Page<TaskResponse> getTasks(
            TaskStatus status, Long assignedTo, Long assignedBy,
            Priority priority, Long regionId, Long seriesId,
            int page, int size, String sortBy, String sortDir,
            CustomUserDetails user) {

        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        // ── Build Specification động ──
        Specification<Task> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // ── Filter theo status ──
            // Nếu client gửi "status=TODO" → WHERE status = 'TODO'
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // ── Filter theo ASSISTANT được gán ──
            // Nếu client gửi "assignedTo=5" → WHERE assistant.id = 5
            if (assignedTo != null) {
                predicates.add(cb.equal(root.get("assistant").get("id"), assignedTo));
            }

            // ── Filter theo MANGAKA giao việc ──
            // Nếu client gửi "assignedBy=2" → WHERE assignedBy.id = 2
            if (assignedBy != null) {
                predicates.add(cb.equal(root.get("assignedBy").get("id"), assignedBy));
            }

            // ── Filter theo priority ──
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }

            // ── Filter theo region ──
            if (regionId != null) {
                predicates.add(cb.equal(root.get("region").get("id"), regionId));
            }

            // ── Filter theo seriesId ──
            // Cần join: Task → Region (region_id) → Page (page_id) → Chapter → Series
            // Vì Region chỉ có pageId (Long), không có @ManyToOne với Page
            // → Không thể root.get("region").get("page") trong JPA criteria
            // → Thay vào đó, query regionIds từ PageRepository trước
            // Logic này được xử lý bên ngoài Specification (xem code dưới)
            if (seriesId != null) {
                // Tìm tất cả regionIds có page thuộc series này
                // (Xử lý riêng bên ngoài vì không có quan hệ JPA)
                List<Long> regionIds = findRegionIdsBySeriesId(seriesId);
                if (regionIds.isEmpty()) {
                    // Không có region nào → trả về predicate luôn sai
                    // (không có task nào khớp)
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("region").get("id").in(regionIds));
                }
            }

            // ── Role-based auto filter ──
            // ASSISTANT: chỉ thấy task của mình (assignedTo = currentUserId)
            String role = user.getRole();
            if ("ASSISTANT".equals(role)) {
                predicates.add(cb.equal(root.get("assistant").get("id"), user.getUserId()));
            }
            // MANGAKA: chỉ thấy task mình giao (assignedBy = currentUserId)
            else if ("MANGAKA".equals(role)) {
                predicates.add(cb.equal(root.get("assignedBy").get("id"), user.getUserId()));
            }
            // TANTOU_EDITOR / EDITORIAL_BOARD: không filter gì thêm → thấy tất cả

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // ── Query và map sang DTO ──
        // findAll(Specification, Pageable) trả về Page<Task>
        // .map(taskMapper::toResponse) → chuyển từng Task → TaskResponse
        // Lưu ý: submissions và attachments trong TaskResponse sẽ là null
        // vì không load (LAZY) và MapStruct không gọi getter
        return taskRepository.findAll(spec, pageable)
                .map(taskMapper::toResponse);
    }

    // ════════════════════════════════════════════════════════════════
    // 2. GET TASK BY ID — Chi tiết 1 task
    // ════════════════════════════════════════════════════════════════

    /**
     * GET /api/tasks/{id}
     * <p>
     * Lấy chi tiết 1 task kèm danh sách submissions (lịch sử nộp bài)
     * và attachments (file đính kèm).
     * <p>
     * 📌 Không phân biệt role — Authenticated đều xem được.
     * <p>
     * 📌 Submissions sắp xếp theo version giảm dần.
     *    Attachments sắp xếp theo uploadedAt tăng dần.
     *
     * @param id ID của task
     * @return TaskResponse — chi tiết task + submissions + attachments
     * @throws AppException 404 — nếu không tìm thấy task
     */
    @Transactional(readOnly = true)
    public TaskResponse getTaskById(Long id) {
        // Tìm task — nếu không có → throw 404
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Task not found: " + id));

        // ── Lấy submissions và attachments ──
        // Vì Task entity dùng FetchType.LAZY cho @OneToMany,
        // nên cần gọi getter để trigger load (hoặc @Transactional giúp LAZY load được)
        // Sắp xếp submissions: version giảm dần (mới nhất trước)
        List<TaskSubmission> submissions = task.getSubmissions();
        submissions.sort((a, b) -> b.getVersion().compareTo(a.getVersion()));
        // Attachments: uploadedAt tăng dần (cũ nhất trước)
        List<TaskAttachment> attachments = task.getAttachments();
        attachments.sort((a, b) -> a.getUploadedAt().compareTo(b.getUploadedAt()));

        // Map sang DTO và trả về
        return taskMapper.toResponse(task);
    }

    // ════════════════════════════════════════════════════════════════
    // 3. GET TASKS BY REGION — Tasks của 1 region
    // ════════════════════════════════════════════════════════════════

    /**
     * GET /api/regions/{regionId}/tasks
     * <p>
     * Lấy tất cả tasks thuộc về 1 region cụ thể.
     * Không kèm submissions & attachments để giảm tải.
     * <p>
     * 📌 Dùng trong workspace — hiển thị tasks của vùng vẽ đang chọn.
     * <p>
     * 📌 Role-based:
     *    - ASSISTANT: chỉ thấy task của mình trong region này
     *    - MANGAKA:  chỉ thấy task mình giao trong region này
     *    - EDITOR:    thấy tất cả task trong region này
     *
     * @param regionId ID của region
     * @param user     User hiện tại (từ JWT)
     * @return List<TaskResponse> danh sách tasks (không kèm submissions/attachments)
     * @throws AppException 404 — nếu region không tồn tại
     */
    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksByRegion(Long regionId, CustomUserDetails user) {
        // Kiểm tra region tồn tại
        if (!regionRepository.existsById(regionId)) {
            throw new AppException(HttpStatus.NOT_FOUND,
                    "Region not found: " + regionId);
        }

        // Lấy tasks của region
        List<Task> tasks = taskRepository.findByRegionIdOrderByAssignedAtDesc(regionId);

        String role = user.getRole();

        // Role-based filter
        List<Task> filteredTasks = tasks.stream()
                .filter(task -> {
                    if ("ASSISTANT".equals(role)) {
                        // ASSISTANT: chỉ thấy task của mình
                        return task.getAssistant() != null
                                && task.getAssistant().getId().equals(user.getUserId());
                    } else if ("MANGAKA".equals(role)) {
                        // MANGAKA: chỉ thấy task mình giao
                        return task.getAssignedBy().getId().equals(user.getUserId());
                    }
                    // TANTOU_EDITOR / EDITORIAL_BOARD: thấy tất cả
                    return true;
                })
                .toList();

        // Map sang DTO
        return filteredTasks.stream()
                .map(taskMapper::toResponse)
                .toList();
    }

    // ════════════════════════════════════════════════════════════════
    // 4. CREATE TASK — Tạo task mới
    // ════════════════════════════════════════════════════════════════

    /**
     * POST /api/regions/{regionId}/tasks
     * <p>
     * MANGAKA tạo task mới và gán cho 1 ASSISTANT.
     * Hệ thống tự động copy pageImageUrl từ region → page sang task.
     * <p>
     * 📌 Quy trình:
     *    1. Kiểm tra region tồn tại
     *    2. Kiểm tra assistant tồn tại và có role ASSISTANT
     *    3. Kiểm tra dueDate ở tương lai (nếu có)
     *    4. Tạo Task entity — copy pageImageUrl từ page
     *    5. Lưu vào database
     *    6. Map sang DTO và trả về
     *
     * @param regionId    ID của region cần giao việc
     * @param request     DTO từ frontend (title bắt buộc, assistantId bắt buộc)
     * @param currentUser User đang đăng nhập (MANGAKA)
     * @return TaskResponse — task vừa tạo
     * @throws AppException 404 — nếu region hoặc user không tồn tại
     * @throws AppException 400 — nếu user không phải ASSISTANT hoặc dueDate ở quá khứ
     */
    public TaskResponse createTask(Long regionId, TaskRequest request, CustomUserDetails currentUser) {
        // ── Bước 1: Kiểm tra region tồn tại ──
        Region region = regionRepository.findById(regionId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Region not found: " + regionId));

        // ── Bước 2: Kiểm tra assistant tồn tại và có role ASSISTANT ──
        User assistant = userRepository.findById(request.getAssistantId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "User not found: " + request.getAssistantId()));

        if (assistant.getRole() != Role.ASSISTANT) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "User " + assistant.getUsername() + " is not an ASSISTANT");
        }

        // ── Bước 3: Kiểm tra dueDate ở tương lai (nếu có) ──
        if (request.getDueDate() != null && request.getDueDate().isBefore(LocalDateTime.now())) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Due date must be in the future");
        }

        // ── Bước 4: Lấy pageImageUrl từ region → page ──
        // Region có pageId (Long), cần tìm Page để lấy webImageUrl
        String pageImageUrl = null;
        if (region.getPageId() != null) {
            com.mangaflow.studio.model.page.Page pageEntity = pageRepository.findById(region.getPageId()).orElse(null);
            if (pageEntity != null) {
                pageImageUrl = pageEntity.getWebImageUrl();
            }
        }

        // ── Bước 5: Tìm User entity cho currentUser ──
        User assignedBy = userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

        // ── Bước 6: Tạo Task entity ──
        Task task = Task.builder()
                .region(region)
                .title(request.getTitle())
                .regionType(request.getRegionType())
                .description(request.getDescription())
                .notes(request.getNotes())
                .referenceImageUrl(request.getReferenceImageUrl())
                .pageImageUrl(pageImageUrl)                          // Copy từ page
                .status(TaskStatus.TODO)                              // Mới tạo → TODO
                .priority(request.getPriority() != null
                        ? request.getPriority()
                        : Priority.MEDIUM)                           // Default MEDIUM
                .assistant(assistant)
                .assignedBy(assignedBy)
                .assignedAt(LocalDateTime.now())
                .dueDate(request.getDueDate())
                .build();

        // ── Bước 7: Lưu vào database ──
        Task savedTask = taskRepository.save(task);

        // ── Bước 8: Map sang DTO và trả về ──
        return taskMapper.toResponse(savedTask);
    }

    // ════════════════════════════════════════════════════════════════
    // 5. UPDATE TASK — Cập nhật thông tin task
    // ════════════════════════════════════════════════════════════════

    /**
     * PUT /api/tasks/{id}
     * <p>
     * Cập nhật thông tin task. Chỉ được sửa khi task đang TODO hoặc REJECTED.
     * <p>
     * 📌 Nguyên tắc:
     *    - Nếu gửi assistantId khác → reset assignedAt = now
     *    - Nếu gửi dueDate mới → cập nhật
     *    - Các field không gửi (null) → giữ nguyên giá trị cũ
     * <p>
     * 📌 Chỉ MANGAKA mới được gọi (kiểm tra ở Controller).
     *
     * @param id          ID của task cần sửa
     * @param request     DTO chứa các field muốn thay đổi (null = giữ nguyên)
     * @param currentUser User đang đăng nhập
     * @return TaskResponse — task đã cập nhật
     * @throws AppException 404 — nếu không tìm thấy task
     * @throws AppException 400 — nếu task không ở TODO/REJECTED
     */
    public TaskResponse updateTask(Long id, TaskUpdateRequest request, CustomUserDetails currentUser) {
        // ── Bước 1: Tìm task ──
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Task not found: " + id));

        // ── Bước 2: Kiểm tra trạng thái ──
        // Chỉ cho phép sửa khi task đang TODO hoặc REJECTED
        // Nếu đã IN_PROGRESS hoặc DONE → không được sửa
        if (task.getStatus() != TaskStatus.TODO && task.getStatus() != TaskStatus.REJECTED) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Can only update task when status is TODO or REJECTED, current: " + task.getStatus());
        }

        // ── Bước 3: Cập nhật từng field (partial update) ──

        // title: nếu không null → cập nhật
        if (request.getTitle() != null) {
            task.setTitle(request.getTitle());
        }

        // regionType: nếu không null → cập nhật (override)
        if (request.getRegionType() != null) {
            task.setRegionType(request.getRegionType());
        }

        // description
        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }

        // notes
        if (request.getNotes() != null) {
            task.setNotes(request.getNotes());
        }

        // referenceImageUrl
        if (request.getReferenceImageUrl() != null) {
            task.setReferenceImageUrl(request.getReferenceImageUrl());
        }

        // priority: nếu không null → cập nhật
        if (request.getPriority() != null) {
            task.setPriority(request.getPriority());
        }

        // dueDate: nếu không null → kiểm tra và cập nhật
        if (request.getDueDate() != null) {
            if (request.getDueDate().isBefore(LocalDateTime.now())) {
                throw new AppException(HttpStatus.BAD_REQUEST,
                        "Due date must be in the future");
            }
            task.setDueDate(request.getDueDate());
        }

        // assistantId: nếu khác → giao lại cho người mới, reset assignedAt
        if (request.getAssistantId() != null
                && !request.getAssistantId().equals(task.getAssistant().getId())) {
            User newAssistant = userRepository.findById(request.getAssistantId())
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                            "User not found: " + request.getAssistantId()));
            if (newAssistant.getRole() != Role.ASSISTANT) {
                throw new AppException(HttpStatus.BAD_REQUEST,
                        "User " + newAssistant.getUsername() + " is not an ASSISTANT");
            }
            task.setAssistant(newAssistant);
            task.setAssignedAt(LocalDateTime.now());  // Reset thời gian giao
        }

        // ── Bước 4: Lưu lại ──
        Task savedTask = taskRepository.save(task);

        // ── Bước 5: Map sang DTO và trả về ──
        return taskMapper.toResponse(savedTask);
    }

    // ════════════════════════════════════════════════════════════════
    // 6. UPDATE TASK STATUS — Đổi trạng thái task
    // ════════════════════════════════════════════════════════════════

    /**
     * PATCH /api/tasks/{id}/status
     * <p>
     * Chuyển trạng thái task theo state machine:
     * <pre>
     *  TODO → IN_PROGRESS : ASSISTANT nhận việc
     *  IN_PROGRESS → REJECTED : MANGAKA từ chối
     *  REJECTED → IN_PROGRESS : ASSISTANT làm lại
     * </pre>
     * <p>
     * (IN_PROGRESS → DONE không hỗ trợ ở đây — dùng submission + review)
     *
     * @param id          ID của task
     * @param newStatus   Trạng thái mới
     * @param currentUser User đang đăng nhập
     * @return TaskResponse — task với status mới
     * @throws AppException 404 — nếu không tìm thấy task
     * @throws AppException 400 — nếu chuyển trạng thái không hợp lệ
     * @throws AppException 403 — nếu không có quyền thực hiện chuyển này
     */
    public TaskResponse updateTaskStatus(Long id, TaskStatus newStatus, CustomUserDetails currentUser) {
        // ── Bước 1: Tìm task ──
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Task not found: " + id));

        TaskStatus currentStatus = task.getStatus();

        // ── Bước 2: Kiểm tra state transition ──
        // Dùng switch expression — rõ ràng, compiler kiểm tra đủ case

        boolean validTransition = true;

        // TODO → IN_PROGRESS: chỉ ASSISTANT được gán
        if (currentStatus == TaskStatus.TODO && newStatus == TaskStatus.IN_PROGRESS) {
            if (task.getAssistant() == null
                    || !task.getAssistant().getId().equals(currentUser.getUserId())) {
                throw new AppException(HttpStatus.FORBIDDEN,
                        "Only the assigned assistant can start this task");
            }
        }
        // IN_PROGRESS → REJECTED: chỉ MANGAKA (người giao)
        else if (currentStatus == TaskStatus.IN_PROGRESS && newStatus == TaskStatus.REJECTED) {
            if (!task.getAssignedBy().getId().equals(currentUser.getUserId())) {
                throw new AppException(HttpStatus.FORBIDDEN,
                        "Only the task creator can reject this task");
            }
        }
        // REJECTED → IN_PROGRESS: chỉ ASSISTANT được gán
        else if (currentStatus == TaskStatus.REJECTED && newStatus == TaskStatus.IN_PROGRESS) {
            if (task.getAssistant() == null
                    || !task.getAssistant().getId().equals(currentUser.getUserId())) {
                throw new AppException(HttpStatus.FORBIDDEN,
                        "Only the assigned assistant can retry this task");
            }
        }
        // Các chuyển khác đều không hợp lệ
        else {
            validTransition = false;
        }

        if (!validTransition) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Cannot change status from " + currentStatus + " to " + newStatus);
        }

        // ── Bước 3: Cập nhật và lưu ──
        task.setStatus(newStatus);
        Task savedTask = taskRepository.save(task);

        return taskMapper.toResponse(savedTask);
    }

    // ════════════════════════════════════════════════════════════════
    // 7. DELETE TASK — Xoá task
    // ════════════════════════════════════════════════════════════════

    /**
     * DELETE /api/tasks/{id}
     * <p>
     * Xoá task. Chỉ xoá được khi task đang ở trạng thái TODO.
     * <p>
     * 📌 Cascade: Xoá task → tự động xoá submissions và attachments
     *    (nhờ cascade = ALL và orphanRemoval = true ở Task entity).
     *
     * @param id ID của task cần xoá
     * @throws AppException 404 — nếu không tìm thấy task
     * @throws AppException 400 — nếu task không ở trạng thái TODO
     */
    public void deleteTask(Long id) {
        // ── Bước 1: Tìm task ──
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Task not found: " + id));

        // ── Bước 2: Kiểm tra trạng thái ──
        // Chỉ xoá được khi TODO (chưa ai nhận)
        if (task.getStatus() != TaskStatus.TODO) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Cannot delete task with status " + task.getStatus()
                            + ". Only TODO tasks can be deleted");
        }

        // ── Bước 3: Xoá record trong database ──
        // Cascade sẽ tự động xoá submissions và attachments
        taskRepository.delete(task);
    }

    // ════════════════════════════════════════════════════════════════
    // 8. GET SUBMISSIONS — Lịch sử nộp bài
    // ════════════════════════════════════════════════════════════════

    /**
     * GET /api/tasks/{taskId}/submissions
     * <p>
     * Lấy lịch sử các lần nộp bài của 1 task, sắp xếp theo version giảm dần.
     *
     * @param taskId ID của task
     * @return List<TaskSubmissionResponse> danh sách submissions (mới nhất trước)
     * @throws AppException 404 — nếu task không tồn tại
     */
    @Transactional(readOnly = true)
    public List<TaskSubmissionResponse> getSubmissions(Long taskId) {
        // Kiểm tra task tồn tại
        if (!taskRepository.existsById(taskId)) {
            throw new AppException(HttpStatus.NOT_FOUND,
                    "Task not found: " + taskId);
        }

        // Lấy submissions, sắp xếp version giảm dần
        return taskSubmissionRepository.findByTaskIdOrderByVersionDesc(taskId)
                .stream()
                .map(taskSubmissionMapper::toResponse)
                .toList();
    }

    // ════════════════════════════════════════════════════════════════
    // 9. SUBMIT TASK — ASSISTANT nộp bài
    // ════════════════════════════════════════════════════════════════

    /**
     * POST /api/tasks/{taskId}/submissions
     * <p>
     * ASSISTANT nộp bài làm cho task.
     * Hệ thống tự động tăng version (lấy version cao nhất hiện tại + 1).
     * <p>
     * 📌 Điều kiện:
     *    - User phải là ASSISTANT được gán cho task này
     *    - Task phải đang IN_PROGRESS hoặc REJECTED
     * <p>
     * 📌 Version:
     *    - Lần nộp đầu → version = 1
     *    - Sau mỗi lần sửa → version + 1
     *    - Các submission cũ vẫn được giữ lại (lịch sử)
     *
     * @param taskId      ID của task cần nộp bài
     * @param request     DTO chứa resultImageUrl (bắt buộc), fileUrl, note
     * @param currentUser User đang đăng nhập (ASSISTANT)
     * @return TaskSubmissionResponse — bài vừa nộp
     * @throws AppException 404 — nếu task không tồn tại
     * @throws AppException 403 — nếu user không phải ASSISTANT được gán
     * @throws AppException 400 — nếu task không ở trạng thái cho phép nộp
     */
    public TaskSubmissionResponse submitTask(Long taskId, TaskSubmissionRequest request,
                                              CustomUserDetails currentUser) {
        // ── Bước 1: Tìm task ──
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Task not found: " + taskId));

        // ── Bước 2: Kiểm tra quyền — chỉ ASSISTANT được gán mới nộp được ──
        if (task.getAssistant() == null
                || !task.getAssistant().getId().equals(currentUser.getUserId())) {
            throw new AppException(HttpStatus.FORBIDDEN,
                    "You are not assigned to this task");
        }

        // ── Bước 3: Kiểm tra trạng thái task ──
        // Chỉ nộp được khi task IN_PROGRESS hoặc REJECTED
        if (task.getStatus() != TaskStatus.IN_PROGRESS
                && task.getStatus() != TaskStatus.REJECTED) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Can only submit when task is IN_PROGRESS or REJECTED, current: "
                            + task.getStatus());
        }

        // ── Bước 4: Tính version tiếp theo ──
        // Lấy submission có version cao nhất → +1
        // Nếu chưa có submission nào → version = 1
        int nextVersion = taskSubmissionRepository
                .findByTaskIdOrderByVersionDesc(taskId)
                .stream()
                .findFirst()                       // Lấy submission mới nhất (version cao nhất)
                .map(s -> s.getVersion() + 1)      // Tăng lên 1
                .orElse(1);                         // Chưa có → bắt đầu từ 1

        // ── Bước 5: Tạo TaskSubmission entity ──
        TaskSubmission submission = TaskSubmission.builder()
                .task(task)
                .resultImageUrl(request.getResultImageUrl())
                .fileUrl(request.getFileUrl())
                .note(request.getNote())
                .version(nextVersion)
                .status(TaskSubmissionStatus.SUBMITTED)  // Vừa nộp → chờ duyệt
                .submittedAt(LocalDateTime.now())
                .build();

        // ── Bước 6: Thêm vào task và lưu ──
        // Dùng cascade: thêm vào task.getSubmissions() → tự động save
        task.getSubmissions().add(submission);
        taskRepository.save(task);

        // ── Bước 7: Map sang DTO và trả về ──
        return taskSubmissionMapper.toResponse(submission);
    }

    // ════════════════════════════════════════════════════════════════
    // 10. REVIEW SUBMISSION — MANGAKA duyệt bài
    // ════════════════════════════════════════════════════════════════

    /**
     * PATCH /api/submissions/{id}/status
     * <p>
     * MANGAKA duyệt bài nộp của ASSISTANT.
     * <p>
     * 📌 Hành vi theo trạng thái duyệt:
     * <pre>
     *  APPROVED           → submission hoàn thành, task → DONE
     *  REVISION_REQUIRED  → yêu cầu sửa, task → IN_PROGRESS
     * </pre>
     * <p>
     * 📌 Điều kiện:
     *    - User phải là MANGAKA đã tạo task này
     *    - Submission phải đang ở trạng thái SUBMITTED
     *    - Chỉ chấp nhận APPROVED hoặc REVISION_REQUIRED
     *
     * @param submissionId ID của submission cần duyệt
     * @param newStatus    Trạng thái duyệt: APPROVED hoặc REVISION_REQUIRED
     * @param currentUser  User đang đăng nhập (MANGAKA)
     * @return TaskSubmissionResponse — submission sau khi duyệt
     * @throws AppException 404 — nếu submission không tồn tại
     * @throws AppException 403 — nếu user không phải MANGAKA tạo task
     * @throws AppException 400 — nếu submission không ở SUBMITTED hoặc status không hợp lệ
     */
    public TaskSubmissionResponse reviewSubmission(Long submissionId,
                                                    TaskSubmissionStatus newStatus,
                                                    CustomUserDetails currentUser) {
        // ── Bước 1: Tìm submission ──
        TaskSubmission submission = taskSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Submission not found: " + submissionId));

        Task task = submission.getTask();

        // ── Bước 2: Kiểm tra quyền ──
        // Chỉ MANGAKA đã tạo task mới duyệt được
        if (!task.getAssignedBy().getId().equals(currentUser.getUserId())) {
            throw new AppException(HttpStatus.FORBIDDEN,
                    "Only the task creator can review submissions");
        }

        // ── Bước 3: Kiểm tra submission đang ở SUBMITTED ──
        // Không thể duyệt lại 1 submission đã duyệt rồi
        if (submission.getStatus() != TaskSubmissionStatus.SUBMITTED) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Can only review SUBMITTED submissions, current: " + submission.getStatus());
        }

        // ── Bước 4: Kiểm tra status hợp lệ ──
        // Chỉ chấp nhận APPROVED hoặc REVISION_REQUIRED
        if (newStatus != TaskSubmissionStatus.APPROVED
                && newStatus != TaskSubmissionStatus.REVISION_REQUIRED) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Status must be APPROVED or REVISION_REQUIRED");
        }

        // ── Bước 5: Cập nhật submission status ──
        submission.setStatus(newStatus);
        taskSubmissionRepository.save(submission);

        // ── Bước 6: Cập nhật task status ──
        // APPROVED → task DONE
        // REVISION_REQUIRED → task IN_PROGRESS (để ASSISTANT sửa)
        if (newStatus == TaskSubmissionStatus.APPROVED) {
            task.setStatus(TaskStatus.DONE);
        } else {
            task.setStatus(TaskStatus.IN_PROGRESS);
        }
        taskRepository.save(task);

        // ── Bước 7: Map sang DTO và trả về ──
        return taskSubmissionMapper.toResponse(submission);
    }

    // ════════════════════════════════════════════════════════════════
    // 11. ADD ATTACHMENT — Đính kèm file tham khảo
    // ════════════════════════════════════════════════════════════════

    /**
     * POST /api/tasks/{taskId}/attachments
     * <p>
     * MANGAKA đính kèm file tham khảo (ảnh mẫu, tài liệu) cho task.
     * <p>
     * 📌 Chỉ MANGAKA tạo task mới được thêm attachment.
     *    (Hoặc có thể mở rộng: bất kỳ MANGAKA nào cũng được — tuỳ config)
     *
     * @param taskId      ID của task
     * @param request     DTO chứa fileUrl
     * @param currentUser User đang đăng nhập
     * @return TaskAttachmentResponse — attachment vừa tạo
     * @throws AppException 404 — nếu task không tồn tại
     * @throws AppException 403 — nếu không phải MANGAKA tạo task
     */
    public TaskAttachmentResponse addAttachment(Long taskId, AttachmentRequest request,
                                                  CustomUserDetails currentUser) {
        // ── Bước 1: Tìm task ──
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Task not found: " + taskId));

        // ── Bước 2: Kiểm tra quyền ──
        // Chỉ MANGAKA tạo task mới được thêm attachment
        if (!task.getAssignedBy().getId().equals(currentUser.getUserId())) {
            throw new AppException(HttpStatus.FORBIDDEN,
                    "Only the task creator can add attachments");
        }

        // ── Bước 3: Tạo attachment entity ──
        TaskAttachment attachment = TaskAttachment.builder()
                .task(task)
                .fileUrl(request.getFileUrl())
                .uploadedAt(LocalDateTime.now())
                .build();

        // ── Bước 4: Thêm vào task và lưu ──
        task.getAttachments().add(attachment);
        taskRepository.save(task);

        // ── Bước 5: Map sang DTO và trả về ──
        return taskAttachmentMapper.toResponse(attachment);
    }

    // ════════════════════════════════════════════════════════════════
    // 12. DELETE ATTACHMENT — Xoá file đính kèm
    // ════════════════════════════════════════════════════════════════

    /**
     * DELETE /api/attachments/{id}
     * <p>
     * Xoá file đính kèm khỏi task.
     * <p>
     * 📌 Chỉ MANGAKA mới được xoá attachment.
     *    (Role check ở Controller)
     *
     * @param id ID của attachment cần xoá
     * @throws AppException 404 — nếu attachment không tồn tại
     */
    public void deleteAttachment(Long id) {
        // ── Bước 1: Tìm attachment ──
        TaskAttachment attachment = taskAttachmentRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Attachment not found: " + id));

        // ── Bước 2: Xoá record trong database ──
        // Vì Task entity có cascade = ALL, orphanRemoval = true,
        // nhưng ở đây xoá trực tiếp attachment repository
        // → chỉ DELETE 1 record trong bảng task_attachments
        taskAttachmentRepository.delete(attachment);
    }

    // ════════════════════════════════════════════════════════════════
    // HELPER — Tìm regionIds theo seriesId
    // ════════════════════════════════════════════════════════════════

    /**
     * Tìm tất cả regionIds thuộc về 1 series.
     * <p>
     * Dùng cho filter seriesId ở GET /api/tasks.
     * <p>
     * Quy trình:
     * 1. Tìm tất cả chapters của series (chapter.seriesId = seriesId)
     * 2. Tìm tất cả pages của các chapters đó (page.chapterId IN chapterIds)
     * 3. Tìm tất cả regions của các pages đó (region.pageId IN pageIds)
     * 4. Trả về danh sách regionIds
     * <p>
     * 📌 Vì Region không có @ManyToOne với Page, cần query thủ công.
     *    Sau này nếu thêm quan hệ JPA, có thể dùng criteria join.
     * <p>
     * 📌 Hiệu năng: Nếu series có nhiều pages (hàng trăm), query này vẫn ổn
     *    vì là truy vấn đơn giản trên indexed column.
     *
     * @param seriesId ID của series
     * @return List<Long> danh sách regionIds (có thể rỗng)
     */
    private List<Long> findRegionIdsBySeriesId(Long seriesId) {
        // ── Bước 1: Lấy tất cả chapters của series ──
        // Dùng ChapterRepository.findBySeriesIdOrderByChapterNumberAsc()
        // (đã có sẵn trong ChapterRepository)
        // Tạm thời query trực tiếp — nếu không có ChapterRepository, cần inject
        List<Long> chapterIds = findChapterIdsBySeriesId(seriesId);
        if (chapterIds.isEmpty()) return List.of();

        // ── Bước 2: Lấy tất cả pageIds của các chapters ──
        List<Long> pageIds = pageRepository.findByChapterIdIn(chapterIds)
                .stream()
                .map(com.mangaflow.studio.model.page.Page::getId)
                .toList();
        if (pageIds.isEmpty()) return List.of();

        // ── Bước 3: Lấy tất cả regionIds của các pages ──
        return regionRepository.findByPageIdIn(pageIds)
                .stream()
                .map(Region::getId)
                .toList();
    }

    /**
     * Helper — lấy chapterIds của 1 series.
     * <p>
     * Dùng ChapterRepository.findBySeriesIdOrderByChapterNumberAsc()
     * để lấy danh sách chapters, sau đó map lấy ids.
     *
     * @param seriesId ID của series
     * @return List<Long> danh sách chapterIds
     */
    private List<Long> findChapterIdsBySeriesId(Long seriesId) {
        return chapterRepository.findBySeriesIdOrderByChapterNumberAsc(seriesId)
                .stream()
                .map(Chapter::getId)
                .toList();
    }
}
