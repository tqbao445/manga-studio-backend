# Tasks API Endpoints — Chi tiết

## Quyền truy cập

| Role | Xem task | Tạo/Sửa/Xoá Task | Nộp bài | Duyệt bài |
|------|----------|------------------|---------|-----------|
| MANAGAKA | ✅ tất cả | ✅ | ❌ | ✅ |
| ASSISTANT | ✅ chỉ task của mình | ❌ | ✅ | ❌ |
| TANTOU_EDITOR | ✅ tất cả | ❌ | ❌ | ❌ |
| EDITORIAL_BOARD | ✅ tất cả | ❌ | ❌ | ❌ |

---

## Danh sách endpoints

| # | Method | Endpoint | Role | Mục đích |
|---|--------|----------|------|----------|
| 1 | GET | `/api/tasks` | Authenticated | Danh sách tasks với filter & phân trang |
| 2 | GET | `/api/tasks/{id}` | Authenticated | Chi tiết 1 task kèm submissions & attachments |
| 3 | GET | `/api/regions/{regionId}/tasks` | Authenticated | Tasks của 1 region |
| 4 | POST | `/api/regions/{regionId}/tasks` | MANAGAKA | Tạo task mới, gán cho ASSISTANT |
| 5 | PUT | `/api/tasks/{id}` | MANAGAKA | Cập nhật thông tin task |
| 6 | PATCH | `/api/tasks/{id}/status` | MANAGAKA / ASSISTANT | Đổi trạng thái task |
| 7 | DELETE | `/api/tasks/{id}` | MANAGAKA | Xoá task (chỉ khi TODO) |
| 8 | GET | `/api/tasks/{taskId}/submissions` | Authenticated | Lịch sử nộp bài theo version |
| 9 | POST | `/api/tasks/{taskId}/submissions` | ASSISTANT | Nộp bài làm |
| 10 | PATCH | `/api/submissions/{id}/status` | MANAGAKA | Duyệt bài (approve / yêu cầu sửa) |
| 11 | POST | `/api/tasks/{taskId}/attachments` | MANAGAKA | Đính kèm file tham khảo |
| 12 | DELETE | `/api/attachments/{id}` | MANAGAKA | Xoá file đính kèm |

---

## Chi tiết từng endpoint

---

### 1. GET /api/tasks

**Mô tả:** Lấy danh sách tasks với filter & phân trang. Tuỳ role mà kết quả trả về khác nhau.

**Role:** Authenticated (tất cả role đã đăng nhập)

**Query Parameters:**

| Param | Type | Required | Default | Mô tả |
|-------|------|----------|---------|-------|
| `status` | String | Không | — | Lọc theo status: `TODO`, `IN_PROGRESS`, `DONE`, `REJECTED` |
| `assignedTo` | Long | Không | — | Lọc theo ID của ASSISTANT được gán |
| `assignedBy` | Long | Không | — | Lọc theo ID của người giao việc |
| `priority` | String | Không | — | Lọc theo priority: `LOW`, `MEDIUM`, `HIGH`, `URGENT` |
| `regionId` | Long | Không | — | Lọc theo region |
| `seriesId` | Long | Không | — | Lọc theo series (join region → page → chapter → series) |
| `page` | Integer | Không | 0 | Số trang (bắt đầu từ 0) |
| `size` | Integer | Không | 20 | Số lượng mỗi trang (tối đa 100) |

**Logic đặc biệt:**
- Nếu user là `ASSISTANT`: tự động filter `assignedTo = currentUserId` (chỉ thấy task của mình), không cần truyền param
- Nếu user là `MANAGAKA`: tự động filter `assignedBy = currentUserId` (chỉ thấy task mình giao)
- Nếu user là `TANTOU_EDITOR` / `EDITORIAL_BOARD`: xem được tất cả task

**Response 200:**
```json
{
  "content": [
    {
      "id": 1,
      "regionId": 10,
      "regionType": "CHARACTER",
      "title": "Vẽ nhân vật chính panel 3",
      "description": "Cần vẽ nhân vật chính trong khung cảnh hành động",
      "pageImageUrl": "https://res.cloudinary.com/.../page3.jpg",
      "status": "TODO",
      "priority": "HIGH",
      "assistant": {
        "id": 5,
        "displayName": "Nguyễn Văn A",
        "avatarUrl": "https://..."
      },
      "assignedBy": {
        "id": 2,
        "displayName": "Tác giả X",
        "avatarUrl": "https://..."
      },
      "assignedAt": "2026-05-30T10:00:00",
      "dueDate": "2026-06-05T00:00:00",
      "createdAt": "2026-05-30T10:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```

**Error responses:**
- `401 Unauthorized`: Chưa đăng nhập
- `403 Forbidden`: Token hết hạn hoặc không hợp lệ

---

### 2. GET /api/tasks/{id}

**Mô tả:** Lấy chi tiết 1 task kèm danh sách submissions (lịch sử nộp bài) và attachments (file đính kèm).

**Role:** Authenticated

**Path Variable:**

| Param | Type | Mô tả |
|-------|------|-------|
| `id` | Long | ID của task |

**Response 200:**
```json
{
  "id": 1,
  "regionId": 10,
  "regionType": "CHARACTER",
  "title": "Vẽ nhân vật chính panel 3",
  "description": "Cần vẽ nhân vật chính trong khung cảnh hành động",
  "notes": "Chú ý biểu cảm khuôn mặt",
  "referenceImageUrl": "https://...ref.jpg",
  "pageImageUrl": "https://...page3.jpg",
  "status": "IN_PROGRESS",
  "priority": "HIGH",
  "assistant": { "id": 5, "displayName": "Nguyễn Văn A" },
  "assignedBy": { "id": 2, "displayName": "Tác giả X" },
  "assignedAt": "2026-05-30T10:00:00",
  "dueDate": "2026-06-05T00:00:00",
  "createdAt": "2026-05-30T10:00:00",
  "submissions": [
    {
      "id": 1,
      "taskId": 1,
      "resultImageUrl": "https://...submit-v1.jpg",
      "fileUrl": "https://...file-v1.psd",
      "note": "Đã vẽ xong, anh xem giúp",
      "version": 1,
      "status": "REVISION_REQUIRED",
      "submittedAt": "2026-06-01T14:00:00"
    },
    {
      "id": 2,
      "taskId": 1,
      "resultImageUrl": "https://...submit-v2.jpg",
      "fileUrl": "https://...file-v2.psd",
      "note": "Đã sửa theo yêu cầu",
      "version": 2,
      "status": "SUBMITTED",
      "submittedAt": "2026-06-02T10:00:00"
    }
  ],
  "attachments": [
    {
      "id": 1,
      "taskId": 1,
      "fileUrl": "https://...ref-asset.png",
      "uploadedAt": "2026-05-30T10:30:00"
    }
  ]
}
```

**Error responses:**
- `404 Not Found`: Task không tồn tại

---

### 3. GET /api/regions/{regionId}/tasks

**Mô tả:** Lấy tất cả tasks thuộc về 1 region cụ thể. Dùng trong workspace để hiển thị tasks của vùng vẽ đang chọn.

**Role:** Authenticated

**Path Variable:**

| Param | Type | Mô tả |
|-------|------|-------|
| `regionId` | Long | ID của region |

**Response 200:** Mảng các task (không kèm submissions & attachments để giảm tải)
```json
[
  {
    "id": 1,
    "regionId": 10,
    "regionType": "CHARACTER",
    "title": "Vẽ nhân vật chính panel 3",
    "status": "TODO",
    "priority": "HIGH",
    "assistant": { "id": 5, "displayName": "Nguyễn Văn A" },
    "assignedBy": { "id": 2, "displayName": "Tác giả X" },
    "assignedAt": "2026-05-30T10:00:00",
    "dueDate": "2026-06-05T00:00:00"
  }
]
```

**Error responses:**
- `404 Not Found`: Region không tồn tại

---

### 4. POST /api/regions/{regionId}/tasks

**Mô tả:** MANAGAKA tạo task mới và gán cho 1 ASSISTANT. Hệ thống tự động copy `pageImageUrl` từ region sang task.

**Role:** `MANAGAKA` (`@PreAuthorize("hasRole('MANAGAKA')")`)

**Path Variable:**

| Param | Type | Mô tả |
|-------|------|-------|
| `regionId` | Long | Region cần giao việc |

**Request Body:**
```json
{
  "title": "Vẽ nhân vật chính panel 3",
  "regionType": "CHARACTER",
  "assistantId": 5,
  "priority": "HIGH",
  "description": "Cần vẽ nhân vật chính trong khung cảnh hành động, chú ý ánh sáng và biểu cảm",
  "notes": "Tham khảo file reference đính kèm",
  "referenceImageUrl": "https://res.cloudinary.com/.../reference.jpg",
  "dueDate": "2026-06-05T00:00:00"
}
```

**Chi tiết các field:**

| Field | Type | Required | Default | Validation |
|-------|------|----------|---------|------------|
| `title` | String | ✅ | — | Not blank, max 255 ký tự |
| `regionType` | String | ❌ | — | Phải là 1 trong: `BACKGROUND`, `CHARACTER`, `TEXT`, `EFFECT`, `TONE`, `OTHER` |
| `assistantId` | Long | ✅ | — | Phải là ID của user có role `ASSISTANT` |
| `priority` | String | ❌ | `MEDIUM` | `LOW`, `MEDIUM`, `HIGH`, `URGENT` |
| `description` | String | ❌ | — | Mô tả chi tiết công việc |
| `notes` | String | ❌ | — | Ghi chú cho ASSISTANT |
| `referenceImageUrl` | String | ❌ | — | URL ảnh tham khảo |
| `dueDate` | String | ❌ | — | Định dạng ISO datetime, phải ở tương lai |

**Logic trong Service:**

```java
public TaskResponse createTask(Long regionId, TaskRequest request, User currentUser) {
    // 1. Kiểm tra region tồn tại
    Region region = regionRepository.findById(regionId)
        .orElseThrow(() -> new ResourceNotFoundException("Region not found: " + regionId));

    // 2. Kiểm tra assistant tồn tại và có role ASSISTANT
    User assistant = userRepository.findById(request.getAssistantId())
        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getAssistantId()));
    if (assistant.getRole() != Role.ASSISTANT) {
        throw new BadRequestException("User " + assistant.getUsername() + " is not an ASSISTANT");
    }

    // 3. Tạo entity
    Task task = Task.builder()
        .region(region)
        .title(request.getTitle())
        .regionType(request.getRegionType())
        .assistant(assistant)
        .assignedBy(currentUser)
        .status(TaskStatus.TODO)
        .priority(request.getPriority() != null ? request.getPriority() : Priority.MEDIUM)
        .description(request.getDescription())
        .notes(request.getNotes())
        .referenceImageUrl(request.getReferenceImageUrl())
        .pageImageUrl(region.getPage().getWebImageUrl())  // tự động copy từ page
        .dueDate(request.getDueDate())
        .assignedAt(LocalDateTime.now())
        .build();

    task = taskRepository.save(task);
    return taskMapper.toResponse(task);
}
```

**Response 201:**
```json
{
  "id": 1,
  "regionId": 10,
  "regionType": "CHARACTER",
  "title": "Vẽ nhân vật chính panel 3",
  "description": "Cần vẽ nhân vật chính trong khung cảnh hành động",
  "pageImageUrl": "https://res.cloudinary.com/.../page3.jpg",
  "status": "TODO",
  "priority": "HIGH",
  "assistant": { "id": 5, "displayName": "Nguyễn Văn A", "avatarUrl": "https://..." },
  "assignedBy": { "id": 2, "displayName": "Tác giả X", "avatarUrl": "https://..." },
  "assignedAt": "2026-05-30T10:00:00",
  "dueDate": "2026-06-05T00:00:00",
  "createdAt": "2026-05-30T10:00:00"
}
```

**Error responses:**
- `400 Bad Request`: Assistant không có role ASSISTANT, hoặc dueDate ở quá khứ
- `404 Not Found`: Region hoặc user không tồn tại

---

### 5. PUT /api/tasks/{id}

**Mô tả:** Cập nhật thông tin task (title, priority, description, notes, assistant, dueDate...). Chỉ được sửa khi task đang ở trạng thái `TODO` hoặc `REJECTED`.

**Role:** `MANAGAKA`

**Path Variable:**

| Param | Type | Mô tả |
|-------|------|-------|
| `id` | Long | ID của task cần sửa |

**Request Body:** Giống `TaskRequest` ở endpoint tạo, nhưng tất cả field đều optional (không gửi field nào thì giữ nguyên giá trị cũ).

```json
{
  "title": "Vẽ nhân vật chính panel 3 (sửa)",
  "priority": "URGENT",
  "assistantId": 7,
  "notes": "Đã thay đổi bố cục, xem lại file tham khảo mới"
}
```

**Logic:**
- Nếu gửi `assistantId` khác → reset `assignedAt = now` (giao lại cho người mới)
- Nếu gửi `dueDate` mới → cập nhật
- Các field không gửi → giữ nguyên

**Response 200:** `TaskResponse` đã cập nhật

**Error responses:**
- `400 Bad Request`: Task không ở trạng thái TODO hoặc REJECTED
- `404 Not Found`: Task không tồn tại

---

### 6. PATCH /api/tasks/{id}/status

**Mô tả:** Chuyển trạng thái task. ASSISTANT nhận task (TODO → IN_PROGRESS), MANAGAKA từ chối (IN_PROGRESS → REJECTED), ASSISTANT làm lại (REJECTED → IN_PROGRESS).

**Role:** `MANAGAKA` hoặc `ASSISTANT` (tuỳ chuyển)

**Path Variable:**

| Param | Type | Mô tả |
|-------|------|-------|
| `id` | Long | ID của task |

**Request Body:**
```json
{
  "status": "IN_PROGRESS"
}
```

**Workflow & validation:**

| Chuyển từ | → | Thành | Ai được làm | Validation |
|-----------|---|-------|-------------|------------|
| `TODO` | → | `IN_PROGRESS` | **ASSISTANT** được gán | Current user phải là task.assistant |
| `IN_PROGRESS` | → | `REJECTED` | **MANAGAKA** | Current user phải là task.assignedBy |
| `REJECTED` | → | `IN_PROGRESS` | **ASSISTANT** được gán | Current user phải là task.assistant |

> **Không hỗ trợ:** `IN_PROGRESS → DONE` — thay vào đó dùng `POST /api/tasks/{id}/submissions` (nộp bài) + `PATCH /api/submissions/{id}/status` (duyệt)

**Logic:**
```java
public TaskResponse updateTaskStatus(Long id, TaskStatus newStatus, User currentUser) {
    Task task = taskRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + id));

    TaskStatus currentStatus = task.getStatus();

    // TODO → IN_PROGRESS: chỉ ASSISTANT được gán
    if (currentStatus == TaskStatus.TODO && newStatus == TaskStatus.IN_PROGRESS) {
        if (!task.getAssistant().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Only assigned assistant can start this task");
        }
    }
    // IN_PROGRESS → REJECTED: chỉ MANAGAKA
    else if (currentStatus == TaskStatus.IN_PROGRESS && newStatus == TaskStatus.REJECTED) {
        if (!task.getAssignedBy().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Only the creator can reject this task");
        }
    }
    // REJECTED → IN_PROGRESS: chỉ ASSISTANT
    else if (currentStatus == TaskStatus.REJECTED && newStatus == TaskStatus.IN_PROGRESS) {
        if (!task.getAssistant().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Only assigned assistant can retry this task");
        }
    }
    else {
        throw new BadRequestException("Cannot change from " + currentStatus + " to " + newStatus);
    }

    task.setStatus(newStatus);
    task = taskRepository.save(task);
    return taskMapper.toResponse(task);
}
```

**Response 200:** `TaskResponse` với status mới

**Error responses:**
- `400 Bad Request`: Chuyển trạng thái không hợp lệ
- `403 Forbidden`: Không có quyền thực hiện chuyển này

---

### 7. DELETE /api/tasks/{id}

**Mô tả:** Xoá task. Chỉ xoá được khi task đang ở trạng thái `TODO` (chưa ai nhận làm).

**Role:** `MANAGAKA`

**Path Variable:**

| Param | Type | Mô tả |
|-------|------|-------|
| `id` | Long | ID của task cần xoá |

**Logic:**
- Kiểm tra task tồn tại
- Nếu status không phải `TODO` → throw `BadRequestException`
- Xoá task (cascade sẽ xoá luôn submissions & attachments)

**Response 204:** No Content

**Error responses:**
- `400 Bad Request`: Task đã được nhận (không phải TODO)
- `404 Not Found`: Task không tồn tại

---

### 8. GET /api/tasks/{taskId}/submissions

**Mô tả:** Lấy lịch sử các lần nộp bài của 1 task, sắp xếp theo version giảm dần (mới nhất trước).

**Role:** Authenticated

**Path Variable:**

| Param | Type | Mô tả |
|-------|------|-------|
| `taskId` | Long | ID của task |

**Response 200:**
```json
[
  {
    "id": 2,
    "taskId": 1,
    "resultImageUrl": "https://...submit-v2.jpg",
    "fileUrl": "https://...file-v2.psd",
    "note": "Đã sửa theo yêu cầu, anh xem lại giúp",
    "version": 2,
    "status": "SUBMITTED",
    "submittedAt": "2026-06-02T10:00:00"
  },
  {
    "id": 1,
    "taskId": 1,
    "resultImageUrl": "https://...submit-v1.jpg",
    "fileUrl": "https://...file-v1.psd",
    "note": "Đã vẽ xong",
    "version": 1,
    "status": "REVISION_REQUIRED",
    "submittedAt": "2026-06-01T14:00:00"
  }
]
```

**Error responses:**
- `404 Not Found`: Task không tồn tại

---

### 9. POST /api/tasks/{taskId}/submissions

**Mô tả:** ASSISTANT nộp bài làm cho task. Hệ thống tự động tăng version (lấy version cao nhất hiện tại + 1). Chỉ nộp được khi task đang `IN_PROGRESS` hoặc `REJECTED`.

**Role:** `ASSISTANT` — chỉ nộp được cho task của mình

**Path Variable:**

| Param | Type | Mô tả |
|-------|------|-------|
| `taskId` | Long | ID của task cần nộp bài |

**Request Body:**
```json
{
  "resultImageUrl": "https://res.cloudinary.com/.../ket-qua.jpg",
  "fileUrl": "https://res.cloudinary.com/.../file-hoan-chinh.psd",
  "note": "Đã vẽ xong nhân vật chính, anh xem giúp em"
}
```

**Chi tiết các field:**

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `resultImageUrl` | String | ✅ | URL ảnh kết quả (JPG/PNG) |
| `fileUrl` | String | ❌ | URL file nguồn (PSD/CLIP) |
| `note` | String | ❌ | Ghi chú cho MANAGAKA |

**Logic:**
```java
public TaskSubmissionResponse submitTask(Long taskId, TaskSubmissionRequest request, User currentUser) {
    Task task = taskRepository.findById(taskId)
        .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

    // Validation
    if (!task.getAssistant().getId().equals(currentUser.getId())) {
        throw new AccessDeniedException("You are not assigned to this task");
    }
    if (task.getStatus() != TaskStatus.IN_PROGRESS && task.getStatus() != TaskStatus.REJECTED) {
        throw new BadRequestException("Can only submit when task is IN_PROGRESS or REJECTED");
    }

    // Auto increment version
    int nextVersion = taskSubmissionRepository.findByTaskIdOrderByVersionDesc(taskId)
        .stream()
        .findFirst()
        .map(s -> s.getVersion() + 1)
        .orElse(1);

    TaskSubmission submission = TaskSubmission.builder()
        .task(task)
        .resultImageUrl(request.getResultImageUrl())
        .fileUrl(request.getFileUrl())
        .note(request.getNote())
        .version(nextVersion)
        .status(TaskSubmissionStatus.SUBMITTED)
        .submittedAt(LocalDateTime.now())
        .build();

    submission = taskSubmissionRepository.save(submission);
    return taskSubmissionMapper.toResponse(submission);
}
```

**Response 201:**
```json
{
  "id": 3,
  "taskId": 1,
  "resultImageUrl": "https://res.cloudinary.com/.../ket-qua.jpg",
  "fileUrl": "https://res.cloudinary.com/.../file-hoan-chinh.psd",
  "note": "Đã vẽ xong nhân vật chính, anh xem giúp em",
  "version": 2,
  "status": "SUBMITTED",
  "submittedAt": "2026-06-02T10:00:00"
}
```

**Error responses:**
- `400 Bad Request`: Task không ở trạng thái IN_PROGRESS hoặc REJECTED
- `403 Forbidden`: User không phải ASSISTANT được gán
- `404 Not Found`: Task không tồn tại

---

### 10. PATCH /api/submissions/{id}/status

**Mô tả:** MANAGAKA duyệt bài nộp của ASSISTANT. Nếu APPROVED → task đánh dấu DONE. Nếu REVISION_REQUIRED → task quay về IN_PROGRESS để ASSISTANT sửa lại.

**Role:** `MANAGAKA` — chỉ duyệt được task do mình tạo

**Path Variable:**

| Param | Type | Mô tả |
|-------|------|-------|
| `id` | Long | ID của submission cần duyệt |

**Request Body:**
```json
{
  "status": "APPROVED"
}
```

**Chi tiết:**

| Giá trị | Ý nghĩa | Task status sau duyệt |
|---------|---------|----------------------|
| `APPROVED` | Chấp nhận bài nộp, task hoàn thành | Task → `DONE` |
| `REVISION_REQUIRED` | Yêu cầu sửa lại | Task → `IN_PROGRESS` |

**Logic:**
```java
public TaskSubmissionResponse reviewSubmission(Long submissionId, TaskSubmissionStatus newStatus, User currentUser) {
    TaskSubmission submission = taskSubmissionRepository.findById(submissionId)
        .orElseThrow(() -> new ResourceNotFoundException("Submission not found: " + submissionId));

    Task task = submission.getTask();

    if (!task.getAssignedBy().getId().equals(currentUser.getId())) {
        throw new AccessDeniedException("Only the task creator can review submissions");
    }
    if (submission.getStatus() != TaskSubmissionStatus.SUBMITTED) {
        throw new BadRequestException("Can only review SUBMITTED submissions");
    }
    if (newStatus != TaskSubmissionStatus.APPROVED && newStatus != TaskSubmissionStatus.REVISION_REQUIRED) {
        throw new BadRequestException("Status must be APPROVED or REVISION_REQUIRED");
    }

    submission.setStatus(newStatus);
    submission = taskSubmissionRepository.save(submission);

    if (newStatus == TaskSubmissionStatus.APPROVED) {
        task.setStatus(TaskStatus.DONE);
    } else {
        task.setStatus(TaskStatus.IN_PROGRESS);
    }
    taskRepository.save(task);

    return taskSubmissionMapper.toResponse(submission);
}
```

**Response 200:**
```json
{
  "id": 1,
  "taskId": 1,
  "resultImageUrl": "https://...submit-v1.jpg",
  "fileUrl": "https://...file-v1.psd",
  "note": "Đã vẽ xong",
  "version": 1,
  "status": "APPROVED",
  "submittedAt": "2026-06-01T14:00:00"
}
```

**Error responses:**
- `400 Bad Request`: Submission không ở trạng thái SUBMITTED
- `403 Forbidden`: User không phải MANAGAKA đã tạo task này
- `404 Not Found`: Submission không tồn tại

---

### 11. POST /api/tasks/{taskId}/attachments

**Mô tả:** MANAGAKA đính kèm file tham khảo (ảnh mẫu, tài liệu hướng dẫn) cho task để ASSISTANT tham khảo trước khi làm.

**Role:** `MANAGAKA`

**Path Variable:**

| Param | Type | Mô tả |
|-------|------|-------|
| `taskId` | Long | ID của task |

**Request Body:**
```json
{
  "fileUrl": "https://res.cloudinary.com/.../tham-khao.jpg"
}
```

**Chi tiết các field:**

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `fileUrl` | String | ✅ | URL file, max 255 ký tự |

**Logic:**
```java
public TaskAttachmentResponse addAttachment(Long taskId, String fileUrl, User currentUser) {
    Task task = taskRepository.findById(taskId)
        .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

    // MANAGAKA chỉ có thể thêm attachment cho task mình tạo
    // (hoặc bỏ qua check này nếu muốn bất kỳ MANAGAKA nào cũng có thể thêm)
    if (!task.getAssignedBy().getId().equals(currentUser.getId())) {
        throw new AccessDeniedException("Only the task creator can add attachments");
    }

    TaskAttachment attachment = TaskAttachment.builder()
        .task(task)
        .fileUrl(fileUrl)
        .uploadedAt(LocalDateTime.now())
        .build();

    attachment = taskAttachmentRepository.save(attachment);
    return taskAttachmentMapper.toResponse(attachment);
}
```

**Response 201:**
```json
{
  "id": 1,
  "taskId": 1,
  "fileUrl": "https://res.cloudinary.com/.../tham-khao.jpg",
  "uploadedAt": "2026-05-30T10:30:00"
}
```

**Error responses:**
- `404 Not Found`: Task không tồn tại

---

### 12. DELETE /api/attachments/{id}

**Mô tả:** Xoá file đính kèm khỏi task.

**Role:** `MANAGAKA`

**Path Variable:**

| Param | Type | Mô tả |
|-------|------|-------|
| `id` | Long | ID của attachment cần xoá |

**Response 204:** No Content

**Error responses:**
- `404 Not Found`: Attachment không tồn tại

---

## Tổng kết

| # | Method | Endpoint | Request Body | Response | Role |
|---|--------|----------|-------------|----------|------|
| 1 | GET | `/api/tasks` | Query params | `Page<TaskResponse>` | Authenticated |
| 2 | GET | `/api/tasks/{id}` | — | `TaskResponse` | Authenticated |
| 3 | GET | `/api/regions/{regionId}/tasks` | — | `TaskResponse[]` | Authenticated |
| 4 | POST | `/api/regions/{regionId}/tasks` | `TaskRequest` | `TaskResponse` (201) | MANAGAKA |
| 5 | PUT | `/api/tasks/{id}` | `TaskRequest` | `TaskResponse` | MANAGAKA |
| 6 | PATCH | `/api/tasks/{id}/status` | `{ status }` | `TaskResponse` | MANAGAKA / ASSISTANT |
| 7 | DELETE | `/api/tasks/{id}` | — | 204 | MANAGAKA |
| 8 | GET | `/api/tasks/{taskId}/submissions` | — | `TaskSubmissionResponse[]` | Authenticated |
| 9 | POST | `/api/tasks/{taskId}/submissions` | `TaskSubmissionRequest` | `TaskSubmissionResponse` (201) | ASSISTANT |
| 10 | PATCH | `/api/submissions/{id}/status` | `{ status }` | `TaskSubmissionResponse` | MANAGAKA |
| 11 | POST | `/api/tasks/{taskId}/attachments` | `{ fileUrl }` | `TaskAttachmentResponse` (201) | MANAGAKA |
| 12 | DELETE | `/api/attachments/{id}` | — | 204 | MANAGAKA |
