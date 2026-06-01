# Task API — Luồng chạy chi tiết

> File này mô tả luồng xử lý của từng API trong module Task,
> từ lúc request đến lúc response, qua các tầng:
> **Controller → Service → Repository → Database**

---

## Mục lục

1. [GET /api/tasks](#1-get-apitasks)
2. [GET /api/tasks/{id}](#2-get-apitasksid)
3. [GET /api/regions/{regionId}/tasks](#3-get-apiregionsregionidtasks)
4. [POST /api/regions/{regionId}/tasks](#4-post-apiregionsregionidtasks)
5. [PUT /api/tasks/{id}](#5-put-apitasksid)
6. [PATCH /api/tasks/{id}/status](#6-patch-apitasksidstatus)
7. [DELETE /api/tasks/{id}](#7-delete-apitasksid)
8. [GET /api/tasks/{taskId}/submissions](#8-get-apitaskstaskidsubmissions)
9. [POST /api/tasks/{taskId}/submissions](#9-post-apitaskstaskidsubmissions)
10. [PATCH /api/submissions/{id}/status](#10-patch-apisubmissionsidstatus)
11. [POST /api/tasks/{taskId}/attachments](#11-post-apitaskstaskidattachments)
12. [DELETE /api/attachments/{id}](#12-delete-apiattachmentsid)
13. [Sơ đồ tổng quan — Database Relationships](#13-sơ-đồ-tổng-quan--database-relationships)

---

## Ký hiệu

```
  REQUEST  ──▶  [Controller]  ──▶  [Service]  ──▶  [Repository]  ──▶  [Database]
  (JSON)        (HTTP nhận)        (Business Logic)   (JPA Query)       (SQL)
                    │
                    ▼
               [Response]
              (JSON trả về)
```

---

## 1. GET /api/tasks

### Mục đích
Lấy danh sách tasks với filter động và phân trang.

### Luồng chi tiết

```
Trình duyệt/FE                    Backend
────────────────────────────────────────────────────────────────────
    │                                   │
    │   GET /api/tasks?status=TODO       │
    │   &assignedTo=5&page=0&size=20     │
    │   Authorization: Bearer <JWT>      │
    │──────────────────────────────────▶ │
    │                                   │
    │                                   │  ┌─── [TaskController.getTasks()]
    │                                   │  │
    │                                   │  │  Bước 1: @PreAuthorize("isAuthenticated()")
    │                                   │  │  Kiểm tra JWT token hợp lệ
    │                                   │  │  Nếu không → 401 Unauthorized
    │                                   │  │
    │                                   │  │  Bước 2: Spring parse query params
    │                                   │  │  status → TaskStatus enum
    │                                   │  │  assignedTo → Long
    │                                   │  │  page → Integer (0)
    │                                   │  │  size → Integer (20)
    │                                   │  │  + @AuthenticationPrincipal → CustomUserDetails
    │                                   │  │
    │                                   │  │  Bước 3: Gọi TaskService.getTasks()
    │                                   │  │─────────────────▶
    │                                   │  │                  │
    │                                   │  │         ┌─── [TaskService.getTasks()]
    │                                   │  │         │
    │                                   │  │         │  Bước 4: Build Specification động
    │                                   │  │         │  (root, query, cb) → List<Predicate>
    │                                   │  │         │  ┌────────────────────────────────────┐
    │                                   │  │         │  │ Predicates được thêm:               │
    │                                   │  │         │  │ - status == 'TODO' (nếu có)          │
    │                                   │  │         │  │ - assistant.id == 5 (nếu có)        │
    │                                   │  │         │  │ - assignedBy.id == 2 (nếu có)       │
    │                                   │  │         │  │ - priority == 'HIGH' (nếu có)       │
    │                                   │  │         │  │ - region.id == 10 (nếu có)          │
    │                                   │  │         │  │ - region.id IN (seriesFilter) (nếu) │
    │                                   │  │         │  │ - ROLE-based filter:                 │
    │                                   │  │         │  │   ASSISTANT → assistant.id = me     │
    │                                   │  │         │  │   MANGAKA → assignedBy.id = me     │
    │                                   │  │         │  └────────────────────────────────────┘
    │                                   │  │         │  Kết quả: WHERE status=? AND ... AND ...
    │                                   │  │         │
    │                                   │  │         │  Bước 5: taskRepository.findAll(spec, pageable)
    │                                   │  │         │─────────────────▶
    │                                   │  │         │                  │
    │                                   │  │         │         ┌─── [TaskRepository]
    │                                   │  │         │         │ extends JpaSpecificationExecutor
    │                                   │  │         │         │ SQL sinh ra:
    │                                   │  │         │         │ SELECT t.* FROM tasks t
    │                                   │  │         │         │ LEFT JOIN users a ON t.assistant_id = a.id
    │                                   │  │         │         │ LEFT JOIN users ab ON t.assigned_by = ab.id
    │                                   │  │         │         │ WHERE t.status = ?
    │                                   │  │         │         │   AND a.id = ?
    │                                   │  │         │         │ ORDER BY t.assigned_at DESC
    │                                   │  │         │         │ OFFSET 0 ROWS FETCH NEXT 20 ROWS ONLY
    │                                   │  │         │         │
    │                                   │  │         │         │ Trả về Page<Task> (Spring Data)
    │                                   │  │         │◀────────────────
    │                                   │  │         │
    │                                   │  │         │  Bước 6: .map(taskMapper::toResponse)
    │                                   │  │         │  MapStruct tạo TaskMapperImpl:
    │                                   │  │         │  for each Task → TaskResponse {
    │                                   │  │         │      regionId = task.region.id
    │                                   │  │         │      assistant = UserBasicDTO(
    │                                   │  │         │          id = task.assistant.id,
    │                                   │  │         │          displayName = task.assistant.displayName,
    │                                   │  │         │          avatarUrl = task.assistant.avatarUrl
    │                                   │  │         │      )
    │                                   │  │         │      assignedBy = UserBasicDTO(...)
    │                                   │  │         │      submissions = null (LAZY, không load)
    │                                   │  │         │      attachments = null (LAZY, không load)
    │                                   │  │         │  }
    │                                   │  │         │
    │                                   │  │         │  Trả về Page<TaskResponse>
    │                                   │  │         │◀────────────────
    │                                   │  │         │
    │                                   │  │  Bước 7: Nhận Page<TaskResponse>
    │                                   │  │  ResponseEntity.ok(body)
    │                                   │  │─────────────────▶
    │                                   │
    │   HTTP 200 + JSON                 │
    │◀──────────────────────────────────│
    │                                   │
    │   {
    │     "content": [
    │       {
    │         "id": 1,
    │         "regionId": 10,
    │         "title": "Vẽ nhân vật chính",
    │         "status": "TODO",
    │         "assistant": {
    │           "id": 5,
    │           "displayName": "Nguyễn Văn A"
    │         },
    │         "assignedAt": "2026-05-30T10:00:00"
    │       }
    │     ],
    │     "page": 0,
    │     "size": 20,
    │     "totalElements": 42,
    │     "totalPages": 3
    │   }
```

### Lỗi có thể xảy ra
| HTTP | Khi nào | Xử lý ở đâu |
|------|---------|-------------|
| 401 | Token thiếu/hết hạn | JwtAuthFilter (trước khi vào Controller) |
| 500 | Lỗi database | GlobalExceptionHandler |

---

## 2. GET /api/tasks/{id}

### Mục đích
Lấy chi tiết 1 task kèm submissions và attachments.

### Luồng chi tiết

```
    │   GET /api/tasks/1                │
    │──────────────────────────────────▶ │
    │                                   │
    │  ┌─── [TaskController.getTaskById()]
    │  │  @PathVariable Long id = 1
    │  │
    │  │  Gọi TaskService.getTaskById(1)
    │  │─────────────────▶
    │  │                  │
    │  │         ┌─── [TaskService.getTaskById()]
    │  │         │
    │  │         │  Bước 1: taskRepository.findById(1)
    │  │         │  SELECT t.* FROM tasks t WHERE t.id = 1
    │  │         │
    │  │         │  Nếu không tìm thấy:
    │  │         │    throw AppException(404, "Task not found: 1")
    │  │         │    → GlobalExceptionHandler → HTTP 404
    │  │         │
    │  │         │  Bước 2: task.getSubmissions()
    │  │         │  Vì @Transactional(readOnly=true) nên
    │  │         │  Hibernate vẫn load được LAZY collection
    │  │         │  SQL: SELECT * FROM task_submissions WHERE task_id = 1
    │  │         │
    │  │         │  Bước 3: Sort submissions theo version DESC
    │  │         │  submissions.sort((a,b) → b.version - a.version)
    │  │         │
    │  │         │  Bước 4: task.getAttachments()
    │  │         │  SQL: SELECT * FROM task_attachments WHERE task_id = 1
    │  │         │
    │  │         │  Bước 5: Sort attachments theo uploadedAt ASC
    │  │         │  attachments.sort((a,b) → a.uploadedAt - b.uploadedAt)
    │  │         │
    │  │         │  Bước 6: taskMapper.toResponse(task)
    │  │         │  MapStruct map:
    │  │         │    - submissions: List<TaskSubmission> → List<TaskSubmissionResponse>
    │  │         │      (nhờ TaskSubmissionMapper có trong uses)
    │  │         │    - attachments: List<TaskAttachment> → List<TaskAttachmentResponse>
    │  │         │      (nhờ TaskAttachmentMapper có trong uses)
    │  │         │
    │  │         │  Trả về TaskResponse (đầy đủ)
    │  │         │◀────────────────
    │  │
    │  │  ResponseEntity.ok(taskResponse)
    │
    │   HTTP 200                         │
    │◀──────────────────────────────────│
    │                                   │
    │   {
    │     "id": 1,
    │     "regionId": 10,
    │     "title": "Vẽ nhân vật chính panel 3",
    │     "status": "IN_PROGRESS",
    │     "submissions": [
    │       { "id": 2, "version": 2, "status": "SUBMITTED" },
    │       { "id": 1, "version": 1, "status": "REVISION_REQUIRED" }
    │     ],
    │     "attachments": [
    │       { "id": 1, "fileUrl": "https://..." }
    │     ]
    │   }
```

---

## 3. GET /api/regions/{regionId}/tasks

### Mục đích
Lấy tasks của 1 region (dùng trong workspace).

### Luồng chi tiết

```
    │   GET /api/regions/10/tasks        │
    │──────────────────────────────────▶ │
    │                                   │
    │  ┌─── [TaskController.getTasksByRegion()]
    │  │  @PathVariable Long regionId = 10
    │  │
    │  │  Gọi TaskService.getTasksByRegion(10, user)
    │  │─────────────────▶
    │  │                  │
    │  │         ┌─── [TaskService.getTasksByRegion()]
    │  │         │
    │  │         │  Bước 1: Kiểm tra region tồn tại
    │  │         │  regionRepository.existsById(10)
    │  │         │  Nếu false → throw 404 "Region not found: 10"
    │  │         │
    │  │         │  Bước 2: Lấy tasks của region
    │  │         │  taskRepository.findByRegionIdOrderByAssignedAtDesc(10)
    │  │         │  SQL: SELECT * FROM tasks WHERE region_id = 10
    │  │         │       ORDER BY assigned_at DESC
    │  │         │
    │  │         │  Bước 3: Role-based filter (Java Stream)
    │  │         │  tasks.stream().filter(task → {
    │  │         │      if (role == ASSISTANT)
    │  │         │          return task.assistant.id == currentUser.id
    │  │         │      if (role == MANGAKA)
    │  │         │          return task.assignedBy.id == currentUser.id
    │  │         │      return true  // EDITOR thấy tất cả
    │  │         │  })
    │  │         │
    │  │         │  Bước 4: Map từng task → TaskResponse
    │  │         │  .map(taskMapper::toResponse)
    │  │         │  (không load submissions/attachments)
    │  │         │
    │  │         │  Trả về List<TaskResponse>
    │  │         │◀────────────────
    │  │
    │  │  ResponseEntity.ok(list)
    │
    │   HTTP 200 + JSON array            │
    │◀──────────────────────────────────│
```

---

## 4. POST /api/regions/{regionId}/tasks

### Mục đích
MANGAKA tạo task mới, gán cho ASSISTANT.

### Luồng chi tiết

```
    │   POST /api/regions/10/tasks       │
    │   Authorization: Bearer <JWT>      │
    │   Content-Type: application/json   │
    │   {                                │
    │     "title": "Vẽ nhân vật chính",  │
    │     "assistantId": 5,              │
    │     "priority": "HIGH",            │
    │     "dueDate": "2026-06-05T00:00"  │
    │   }                                │
    │──────────────────────────────────▶ │
    │                                   │
    │  ┌─── [TaskController.createTask()]
    │  │  @PreAuthorize("hasRole('MANGAKA')")
    │  │  Nếu role ≠ MANGAKA → 403 Forbidden
    │  │
    │  │  @Valid @RequestBody TaskRequest
    │  │  Spring Validator kiểm tra:
    │  │    - title: NotBlank, max 255 ✅
    │  │    - assistantId: NotNull ✅
    │  │  Nếu fail → 400 Bad Request
    │  │
    │  │  Gọi TaskService.createTask(10, request, user)
    │  │─────────────────▶
    │  │                  │
    │  │         ┌─── [TaskService.createTask()]
    │  │         │
    │  │         │  Bước 1: Kiểm tra region tồn tại
    │  │         │  regionRepository.findById(10)
    │  │         │  Nếu không → 404
    │  │         │
    │  │         │  Bước 2: Kiểm tra assistant tồn tại & role
    │  │         │  userRepository.findById(5)
    │  │         │  Nếu không → 404
    │  │         │  Nếu user.role ≠ ASSISTANT → 400
    │  │         │  "User x is not an ASSISTANT"
    │  │         │
    │  │         │  Bước 3: Kiểm tra dueDate (nếu có)
    │  │         │  Nếu dueDate < now → 400
    │  │         │  "Due date must be in the future"
    │  │         │
    │  │         │  Bước 4: Lấy pageImageUrl
    │  │         │  pageRepository.findById(region.pageId)
    │  │         │  → page.webImageUrl
    │  │         │  (Nếu page không tồn tại → null)
    │  │         │
    │  │         │  Bước 5: Tìm currentUser entity
    │  │         │  userRepository.findById(currentUser.userId)
    │  │         │
    │  │         │  Bước 6: Tạo Task entity bằng Builder
    │  │         │  Task.builder()
    │  │         │    .region(region)          // từ bước 1
    │  │         │    .title(request.title)
    │  │         │    .assistant(assistant)    // từ bước 2
    │  │         │    .assignedBy(assignedBy)  // từ bước 5
    │  │         │    .status(TODO)
    │  │         │    .priority(request.priority ?? MEDIUM)
    │  │         │    .pageImageUrl(pageImageUrl) // từ bước 4
    │  │         │    .assignedAt(LocalDateTime.now())
    │  │         │    .build()
    │  │         │
    │  │         │  Bước 7: taskRepository.save(task)
    │  │         │  INSERT INTO tasks (...) VALUES (...)
    │  │         │  → Task có id
    │  │         │
    │  │         │  Bước 8: taskMapper.toResponse(task)
    │  │         │  Trả về TaskResponse
    │  │         │◀────────────────
    │  │
    │  │  ResponseEntity.status(CREATED).body(response)
    │
    │   HTTP 201 + JSON                   │
    │◀──────────────────────────────────│
    │   {
    │     "id": 1,
    │     "regionId": 10,
    │     "title": "Vẽ nhân vật chính",
    │     "status": "TODO",
    │     "assistant": { "id": 5, "displayName": "Nguyễn Văn A" },
    │     "assignedBy": { "id": 2, "displayName": "Tác giả X" },
    │     "pageImageUrl": "https://...page3.jpg"
    │   }
```

### Sơ đồ Database INSERT

```
tasks
┌─────┬───────────┬──────────────┬──────────────┬────────┬──────────┐
│ id  │ region_id │ title        │ assistant_id │ status │ priority │
├─────┼───────────┼──────────────┼──────────────┼────────┼──────────┤
│ 1   │ 10        │ Vẽ nhân vật  │ 5            │ TODO   │ HIGH     │
└─────┴───────────┴──────────────┴──────────────┴────────┴──────────┘
```

---

## 5. PUT /api/tasks/{id}

### Mục đích
Cập nhật task. Chỉ sửa được khi TODO hoặc REJECTED.

### Luồng chi tiết

```
    │   PUT /api/tasks/1                 │
    │   Authorization: Bearer <JWT>      │
    │   {                                │
    │     "title": "Vẽ lại nhân vật",    │
    │     "priority": "URGENT",          │
    │     "assistantId": 7               │
    │   }                                │
    │──────────────────────────────────▶ │
    │                                   │
    │  ┌─── [TaskController.updateTask()]
    │  │  @PreAuthorize("hasRole('MANGAKA')")
    │  │
    │  │  Gọi TaskService.updateTask(1, request, user)
    │  │─────────────────▶
    │  │                  │
    │  │         ┌─── [TaskService.updateTask()]
    │  │         │
    │  │         │  Bước 1: taskRepository.findById(1)
    │  │         │  Nếu không → 404
    │  │         │
    │  │         │  Bước 2: Kiểm tra status
    │  │         │  task.status == TODO ✅ hoặc REJECTED ✅
    │  │         │  Nếu IN_PROGRESS hoặc DONE → 400
    │  │         │
    │  │         │  Bước 3: Cập nhật từng field (partial)
    │  │         │  request.title != null → task.setTitle(request.title)
    │  │         │  request.priority != null → task.setPriority(URGENT)
    │  │         │  request.description != null → ...
    │  │         │  request.notes != null → ...
    │  │         │  request.referenceImageUrl != null → ...
    │  │         │  request.dueDate != null → kiểm tra future → set
    │  │         │  request.regionType != null → set
    │  │         │
    │  │         │  Bước 4: Xử lý đổi assistant
    │  │         │  request.assistantId (7) ≠ task.assistant.id (5)
    │  │         │  → Tìm user 7
    │  │         │  → Kiểm tra role ASSISTANT
    │  │         │  → task.setAssistant(newAssistant)
    │  │         │  → task.setAssignedAt(now) // RESET thời gian
    │  │         │
    │  │         │  Bước 5: taskRepository.save(task)
    │  │         │  UPDATE tasks SET title=?, priority=?, assistant_id=?
    │  │         │  WHERE id = 1
    │  │         │
    │  │         │  Bước 6: taskMapper.toResponse(task)
    │  │         │◀────────────────
    │  │
    │  │  ResponseEntity.ok(response)
    │
    │   HTTP 200                          │
    │◀──────────────────────────────────│
    │   {
    │     "id": 1,
    │     "title": "Vẽ lại nhân vật",
    │     "priority": "URGENT",
    │     "assistant": { "id": 7, "displayName": "Trần Văn B" },
    │     "assignedAt": "2026-05-31T21:00:00"  // đã reset
    │   }
```

---

## 6. PATCH /api/tasks/{id}/status

### Mục đích
Chuyển trạng thái task (state machine).

### Luồng chi tiết

```
    │   PATCH /api/tasks/1/status        │
    │   Authorization: Bearer <JWT>      │
    │   { "status": "IN_PROGRESS" }      │
    │──────────────────────────────────▶ │
    │                                   │
    │  ┌─── [TaskController.updateTaskStatus()]
    │  │  @PreAuthorize("hasAnyRole('MANGAKA', 'ASSISTANT')")
    │  │
    │  │  @Valid → TaskStatusRequest.status != null
    │  │
    │  │  Gọi TaskService.updateTaskStatus(1, IN_PROGRESS, user)
    │  │─────────────────▶
    │  │                  │
    │  │         ┌─── [TaskService.updateTaskStatus()]
    │  │         │
    │  │         │  Bước 1: taskRepository.findById(1)
    │  │         │  Nếu không → 404
    │  │         │
    │  │         │  Bước 2: Kiểm tra state transition
    │  │         │
    │  │         │  current = TODO, new = IN_PROGRESS
    │  │         │  ┌─ TODO → IN_PROGRESS ─┐
    │  │         │  │ Check: currentUser == task.assistant? │
    │  │         │  │ task.assistant.id == currentUser.id?  │
    │  │         │  │ Nếu không → 403 FORBIDDEN             │
    │  │         │  └───────────────────────────────────────┘
    │  │         │
    │  │         │  ┌─ IN_PROGRESS → REJECTED ────────────┐
    │  │         │  │ Check: currentUser == task.assignedBy? │
    │  │         │  │ task.assignedBy.id == currentUser.id? │
    │  │         │  │ Nếu không → 403 FORBIDDEN             │
    │  │         │  └───────────────────────────────────────┘
    │  │         │
    │  │         │  ┌─ REJECTED → IN_PROGRESS ────────────┐
    │  │         │  │ Check: currentUser == task.assistant? │
    │  │         │  │ Nếu không → 403 FORBIDDEN             │
    │  │         │  └───────────────────────────────────────┘
    │  │         │
    │  │         │  Các chuyển khác → 400 BAD_REQUEST
    │  │         │
    │  │         │  Bước 3: task.setStatus(IN_PROGRESS)
    │  │         │  taskRepository.save(task)
    │  │         │  UPDATE tasks SET status = 'IN_PROGRESS'
    │  │         │  WHERE id = 1
    │  │         │
    │  │         │  Bước 4: taskMapper.toResponse(task)
    │  │         │◀────────────────
    │  │
    │  │  ResponseEntity.ok(response)
    │
    │   HTTP 200                          │
    │◀──────────────────────────────────│
    │   {
    │     "id": 1,
    │     "status": "IN_PROGRESS",
    │     "assistant": { "id": 5, "displayName": "Nguyễn Văn A" }
    │   }
```

### State Machine Diagram

```
                    ┌──────────────────────────────────────────┐
                    │           ASSISTANT nhận việc             │
                    │         (check: là người được gán)        │
                    │                                          │
    ┌───────┐      TODO ──────────────────────────────────▶ IN_PROGRESS
    │ Xoá được│       │                                              │
    └───────┘       │                                              │
                    │                                              │
                    │                                    MANGAKA từ chối
                    │                                 (check: là người giao)
                    │                                              │
                    │                                              ▼
                    │                                          REJECTED
                    │                                              │
                    │                                              │
                    └─────── ASSISTANT làm lại ────────────────────┘
                          (check: là người được gán)

    Lưu ý: IN_PROGRESS → DONE thông qua submission + review riêng
```

---

## 7. DELETE /api/tasks/{id}

### Mục đích
Xoá task. Chỉ xoá được TODO.

### Luồng chi tiết

```
    │   DELETE /api/tasks/1              │
    │   Authorization: Bearer <JWT>      │
    │──────────────────────────────────▶ │
    │                                   │
    │  ┌─── [TaskController.deleteTask()]
    │  │  @PreAuthorize("hasRole('MANGAKA')")
    │  │
    │  │  Gọi TaskService.deleteTask(1)
    │  │─────────────────▶
    │  │                  │
    │  │         ┌─── [TaskService.deleteTask()]
    │  │         │
    │  │         │  Bước 1: taskRepository.findById(1)
    │  │         │  Nếu không → 404
    │  │         │
    │  │         │  Bước 2: Kiểm tra status
    │  │         │  task.status == TODO? ✅
    │  │         │  Nếu IN_PROGRESS/DONE/REJECTED → 400
    │  │         │  "Cannot delete task with status ..."
    │  │         │
    │  │         │  Bước 3: taskRepository.delete(task)
    │  │         │  Cascade ALL + orphanRemoval = true
    │  │         │  → Hibernate tự động DELETE submissions
    │  │         │  → Hibernate tự động DELETE attachments
    │  │         │  → DELETE tasks WHERE id = 1
    │  │         │
    │  │         │  SQL thực tế (cascade):
    │  │         │  DELETE FROM task_submissions WHERE task_id = 1
    │  │         │  DELETE FROM task_attachments WHERE task_id = 1
    │  │         │  DELETE FROM tasks WHERE id = 1
    │  │         │◀────────────────
    │  │
    │  │  ResponseEntity.noContent().build()
    │
    │   HTTP 204 (No Content)            │
    │◀──────────────────────────────────│
```

---

## 8. GET /api/tasks/{taskId}/submissions

### Mục đích
Lấy lịch sử nộp bài của 1 task.

### Luồng chi tiết

```
    │   GET /api/tasks/1/submissions     │
    │──────────────────────────────────▶ │
    │                                   │
    │  ┌─── [TaskController.getSubmissions()]
    │  │  @PathVariable Long taskId = 1
    │  │
    │  │  Gọi TaskService.getSubmissions(1)
    │  │─────────────────▶
    │  │                  │
    │  │         ┌─── [TaskService.getSubmissions()]
    │  │         │
    │  │         │  Bước 1: taskRepository.existsById(1)
    │  │         │  Nếu false → 404
    │  │         │
    │  │         │  Bước 2: taskSubmissionRepository
    │  │         │    .findByTaskIdOrderByVersionDesc(1)
    │  │         │  SQL: SELECT * FROM task_submissions
    │  │         │       WHERE task_id = 1
    │  │         │       ORDER BY version DESC
    │  │         │
    │  │         │  Bước 3: .stream()
    │  │         │    .map(taskSubmissionMapper::toResponse)
    │  │         │    .toList()
    │  │         │  MapStruct:
    │  │         │    taskId = submission.task.id
    │  │         │    (các field còn lại map tự động)
    │  │         │
    │  │         │  Trả về List<TaskSubmissionResponse>
    │  │         │◀────────────────
    │  │
    │  │  ResponseEntity.ok(list)
    │
    │   HTTP 200                          │
    │◀──────────────────────────────────│
    │   [
    │     { "id": 2, "version": 2, "status": "SUBMITTED" },
    │     { "id": 1, "version": 1, "status": "REVISION_REQUIRED" }
    │   ]
```

---

## 9. POST /api/tasks/{taskId}/submissions

### Mục đích
ASSISTANT nộp bài làm. Auto increment version.

### Luồng chi tiết

```
    │   POST /api/tasks/1/submissions    │
    │   Authorization: Bearer <JWT>      │
    │   {                                │
    │     "resultImageUrl": "https://...",
    │     "fileUrl": "https://...",
    │     "note": "Đã vẽ xong"
    │   }                                │
    │──────────────────────────────────▶ │
    │                                   │
    │  ┌─── [TaskController.submitTask()]
    │  │  @PreAuthorize("hasRole('ASSISTANT')")
    │  │
    │  │  Gọi TaskService.submitTask(1, request, user)
    │  │─────────────────▶
    │  │                  │
    │  │         ┌─── [TaskService.submitTask()]
    │  │         │
    │  │         │  Bước 1: taskRepository.findById(1)
    │  │         │  Nếu không → 404
    │  │         │
    │  │         │  Bước 2: Kiểm tra quyền
    │  │         │  task.assistant.id == currentUser.id?
    │  │         │  Nếu không → 403 "You are not assigned"
    │  │         │
    │  │         │  Bước 3: Kiểm tra task status
    │  │         │  task.status == IN_PROGRESS? ✅
    │  │         │  task.status == REJECTED? ✅
    │  │         │  Nếu TODO/DONE → 400
    │  │         │
    │  │         │  Bước 4: Tính version tiếp theo
    │  │         │  taskSubmissionRepository
    │  │         │    .findByTaskIdOrderByVersionDesc(1)
    │  │         │    .stream().findFirst()
    │  │         │  Nếu có submission cũ → version = max + 1
    │  │         │  Nếu chưa có → version = 1
    │  │         │
    │  │         │  Ví dụ: có submission version 1
    │  │         │  → nextVersion = 2
    │  │         │
    │  │         │  Bước 5: Tạo TaskSubmission entity
    │  │         │  TaskSubmission.builder()
    │  │         │    .task(task)
    │  │         │    .resultImageUrl(request.resultImageUrl)
    │  │         │    .fileUrl(request.fileUrl)
    │  │         │    .note(request.note)
    │  │         │    .version(nextVersion)  // = 2
    │  │         │    .status(SUBMITTED)
    │  │         │    .submittedAt(now)
    │  │         │    .build()
    │  │         │
    │  │         │  Bước 6: Cascade save
    │  │         │  task.submissions.add(submission)
    │  │         │  taskRepository.save(task)
    │  │         │  → INSERT INTO task_submissions (...)
    │  │         │
    │  │         │  Bước 7: taskSubmissionMapper.toResponse()
    │  │         │◀────────────────
    │  │
    │  │  ResponseEntity.status(CREATED).body(response)
    │
    │   HTTP 201                          │
    │◀──────────────────────────────────│
    │   {
    │     "id": 3,
    │     "taskId": 1,
    │     "resultImageUrl": "https://...",
    │     "version": 2,
    │     "status": "SUBMITTED",
    │     "submittedAt": "2026-05-31T21:00:00"
    │   }
```

### Sơ đồ Database INSERT

```
task_submissions (sau 2 lần nộp)
┌─────┬─────────┬─────────────────────┬─────────┬───────────┬──────────────┐
│ id  │ task_id │ resultImageUrl      │ version │ status    │ submittedAt  │
├─────┼─────────┼─────────────────────┼─────────┼───────────┼──────────────┤
│ 1   │ 1       │ https://...v1.jpg   │ 1       │ REVISION  │ 2026-06-01   │
│ 2   │ 1       │ https://...v2.jpg   │ 2       │ SUBMITTED │ 2026-06-02   │
└─────┴─────────┴─────────────────────┴─────────┴───────────┴──────────────┘
```

---

## 10. PATCH /api/submissions/{id}/status

### Mục đích
MANGAKA duyệt bài nộp. Ảnh hưởng đến task status.

### Luồng chi tiết

```
    │   PATCH /api/submissions/1/status  │
    │   Authorization: Bearer <JWT>      │
    │   { "status": "APPROVED" }         │
    │──────────────────────────────────▶ │
    │                                   │
    │  ┌─── [TaskController.reviewSubmission()]
    │  │  @PreAuthorize("hasRole('MANGAKA')")
    │  │
    │  │  Gọi TaskService.reviewSubmission(1, APPROVED, user)
    │  │─────────────────▶
    │  │                  │
    │  │         ┌─── [TaskService.reviewSubmission()]
    │  │         │
    │  │         │  Bước 1: taskSubmissionRepository.findById(1)
    │  │         │  Nếu không → 404
    │  │         │  Lấy task = submission.task
    │  │         │
    │  │         │  Bước 2: Kiểm tra quyền
    │  │         │  task.assignedBy.id == currentUser.id?
    │  │         │  Nếu không → 403
    │  │         │
    │  │         │  Bước 3: Kiểm tra submission status
    │  │         │  submission.status == SUBMITTED? ✅
    │  │         │  Nếu APPROVED/REVISION_REQUIRED → 400
    │  │         │
    │  │         │  Bước 4: Kiểm tra newStatus
    │  │         │  newStatus == APPROVED? ✅ → hợp lệ
    │  │         │  newStatus == REVISION_REQUIRED? ✅ → hợp lệ
    │  │         │  Khác → 400
    │  │         │
    │  │         │  Bước 5: Cập nhật submission + task
    │  │         │  ┌──────────────────────────────────────────────┐
    │  │         │  │ Nếu APPROVED:                                │
    │  │         │  │   submission.status = APPROVED               │
    │  │         │  │   task.status = DONE                         │
    │  │         │  │                                              │
    │  │         │  │ Nếu REVISION_REQUIRED:                       │
    │  │         │  │   submission.status = REVISION_REQUIRED      │
    │  │         │  │   task.status = IN_PROGRESS                  │
    │  │         │  └──────────────────────────────────────────────┘
    │  │         │
    │  │         │  Bước 6: Save cả 2 entity
    │  │         │  taskSubmissionRepository.save(submission)
    │  │         │  taskRepository.save(task)
    │  │         │
    │  │         │  SQL (APPROVED):
    │  │         │  UPDATE task_submissions SET status='APPROVED' WHERE id=1
    │  │         │  UPDATE tasks SET status='DONE' WHERE id=1
    │  │         │
    │  │         │  Bước 7: Map → TaskSubmissionResponse
    │  │         │◀────────────────
    │  │
    │  │  ResponseEntity.ok(response)
    │
    │   HTTP 200                          │
    │◀──────────────────────────────────│
    │   {
    │     "id": 1,
    │     "taskId": 1,
    │     "version": 1,
    │     "status": "APPROVED"
    │   }
    │
    │   (Đồng thời task 1 đã chuyển sang DONE)
```

### Sơ đồ cập nhật Database

```
task_submissions (sau duyệt)
┌─────┬─────────┬──────────┬──────────┐
│ id  │ task_id │ version  │ status   │
├─────┼─────────┼──────────┼──────────┤
│ 1   │ 1       │ 1        │ APPROVED │ ← đã cập nhật
└─────┴─────────┴──────────┴──────────┘

tasks (sau duyệt)
┌─────┬──────────┐
│ id  │ status   │
├─────┼──────────┤
│ 1   │ DONE     │ ← đã cập nhật
└─────┴──────────┘
```

---

## 11. POST /api/tasks/{taskId}/attachments

### Mục đích
MANGAKA đính kèm file tham khảo.

### Luồng chi tiết

```
    │   POST /api/tasks/1/attachments    │
    │   Authorization: Bearer <JWT>      │
    │   { "fileUrl": "https://...tham-khao.jpg" }
    │──────────────────────────────────▶ │
    │                                   │
    │  ┌─── [TaskController.addAttachment()]
    │  │  @PreAuthorize("hasRole('MANGAKA')")
    │  │
    │  │  Gọi TaskService.addAttachment(1, request, user)
    │  │─────────────────▶
    │  │                  │
    │  │         ┌─── [TaskService.addAttachment()]
    │  │         │
    │  │         │  Bước 1: taskRepository.findById(1)
    │  │         │  Nếu không → 404
    │  │         │
    │  │         │  Bước 2: Kiểm tra quyền
    │  │         │  task.assignedBy.id == currentUser.id?
    │  │         │  Nếu không → 403
    │  │         │
    │  │         │  Bước 3: Tạo TaskAttachment entity
    │  │         │  TaskAttachment.builder()
    │  │         │    .task(task)
    │  │         │    .fileUrl(request.fileUrl)
    │  │         │    .uploadedAt(now)
    │  │         │    .build()
    │  │         │
    │  │         │  Bước 4: Cascade save
    │  │         │  task.attachments.add(attachment)
    │  │         │  taskRepository.save(task)
    │  │         │  → INSERT INTO task_attachments (...)
    │  │         │
    │  │         │  Bước 5: taskAttachmentMapper.toResponse()
    │  │         │◀────────────────
    │  │
    │  │  ResponseEntity.status(CREATED).body(response)
    │
    │   HTTP 201                          │
    │◀──────────────────────────────────│
    │   {
    │     "id": 1,
    │     "taskId": 1,
    │     "fileUrl": "https://...tham-khao.jpg",
    │     "uploadedAt": "2026-05-30T10:30:00"
    │   }
```

---

## 12. DELETE /api/attachments/{id}

### Mục đích
Xoá file đính kèm.

### Luồng chi tiết

```
    │   DELETE /api/attachments/1        │
    │   Authorization: Bearer <JWT>      │
    │──────────────────────────────────▶ │
    │                                   │
    │  ┌─── [TaskController.deleteAttachment()]
    │  │  @PreAuthorize("hasRole('MANGAKA')")
    │  │
    │  │  Gọi TaskService.deleteAttachment(1)
    │  │─────────────────▶
    │  │                  │
    │  │         ┌─── [TaskService.deleteAttachment()]
    │  │         │
    │  │         │  Bước 1: taskAttachmentRepository.findById(1)
    │  │         │  Nếu không → 404
    │  │         │
    │  │         │  Bước 2: taskAttachmentRepository.delete(attachment)
    │  │         │  DELETE FROM task_attachments WHERE id = 1
    │  │         │◀────────────────
    │  │
    │  │  ResponseEntity.noContent().build()
    │
    │   HTTP 204 (No Content)            │
    │◀──────────────────────────────────│
```

---

## 13. Sơ đồ tổng quan — Database Relationships

```
 ┌──────────┐
 │  series  │
 ├──────────┤
 │ id (PK)  │
 └─────┬────┘
       │ 1
       │
       │ *
 ┌─────▼──────┐
 │  chapter   │
 ├────────────┤
 │ id (PK)    │
 │ series_id  │──┐ FK → series.id
 └─────┬──────┘  │
       │ 1       │
       │         │
       │ *       │
 ┌─────▼────┐    │
 │  pages   │    │
 ├──────────┤    │
 │ id (PK)  │    │
 │ chapterId│────┘ FK → chapter.id
 └─────┬────┘
       │ 1
       │
       │ *
 ┌─────▼──────────┐
 │    region      │
 ├────────────────┤
 │ id (PK)        │
 │ page_id        │────── FK → pages.id (raw Long)
 └─────┬──────────┘
       │ 1
       │
       │ *
 ┌─────▼──────────┐
 │     task       │
 ├────────────────┤
 │ id (PK)        │
 │ region_id      │────── FK → region.id
 │ assistant_id   │────── FK → users.id
 │ assigned_by    │────── FK → users.id
 │ status         │
 │ priority       │
 └─────┬──────────┘
       │ 1
       │
       ├─────────────────────┐
       │ *                   │ *
 ┌─────▼────────────┐  ┌─────▼──────────────┐
 │ task_submissions │  │ task_attachments    │
 ├──────────────────┤  ├────────────────────┤
 │ id (PK)          │  │ id (PK)            │
 │ task_id          │  │ task_id            │
 │ version          │  │ file_url           │
 │ status           │  │ uploaded_at        │
 │ submitted_at     │  └────────────────────┘
 └──────────────────┘

Quan hệ:
  series 1──N chapter
  chapter 1──N pages
  pages  1──N region
  region 1──N task
  task   1──N task_submissions
  task   1──N task_attachments
```
