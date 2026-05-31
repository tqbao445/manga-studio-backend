# Kế hoạch phát triển — MangaFlow Workspace

## Tổng kết công việc đã làm

### 1. Fix lỗi deadline date

**Vấn đề:** Frontend gửi `"2026-05-21"` (date-only) nhưng backend nhận `LocalDateTime` → lỗi JSON parse.

**Đã sửa:** Đổi kiểu `LocalDateTime → LocalDate` ở 3 file:

| File | Dòng | Thay đổi |
|------|------|----------|
| `dto/chapter/request/ChapterRequest.java` | 20 | `LocalDateTime deadline → LocalDate deadline` |
| `model/chapter/Chapter.java` | 44 | `LocalDateTime deadline → LocalDate deadline` |
| `dto/chapter/response/ChapterResponse.java` | 40 | `LocalDateTime deadline → LocalDate deadline` |
| `docs/index.sql` | 92 | `DATETIME → DATE` |

**Branch:** `fix/deadline-localdate` (đã merge vào main)

---

### 2. Docker setup

Đã tạo infrastructure cho backend repo:

| File | Nội dung |
|------|----------|
| `Dockerfile` | Multi-stage: Maven 3.9 build → Temurin 21 JRE runtime |
| `docker-compose.yml` | 3 services: `sqlserver` (2022) + `db-setup` (tạo DB) + `backend` |
| `.env.example` | Mẫu biến môi trường (MSSQL_SA_PASSWORD) |
| `.dockerignore` | Loại trừ target/, .git/, .idea/ |
| `.gitignore` | Thêm `.env` |
| `DOCKER.md` | Hướng dẫn sử dụng Docker cho team |

**Branch:** `feature/docker-setup`

---

### 3. Phân tích Workspace

Đã xác định các tính năng workspace cần xây dựng:

| Module | API cần tạo |
|--------|-------------|
| Region | CRUD + status |
| Task | CRUD + kanban + filter |
| TaskSubmission | Submit + approve/revision |
| Comment | CRUD + reply + resolve |
| Layer | CRUD + reorder |
| Annotation | CRUD |
| Notification | CRUD + markRead |
| BoardVote | Vote approve/reject |
| Ranking | Nhập vote + tính tier |
| Schedule | Publishing calendar |
| DashboardStats | Aggregated stats per role |
| ActivityFeed | Recent activity |

---

### 4. Kế hoạch chi tiết — Module TASKS

#### Tổng quan

**Mục tiêu:** Xây dựng Task Management để MANGAKA giao việc vẽ từng vùng (region) trên page cho ASSISTANT, ASSISTANT nhận & nộp bài, MANGAKA duyệt.

**Phạm vi:** 3 tables SQL (`task`, `task_submission`, `task_attachment`) + frontend hiện tại đang dùng mock data.

---

## Kế hoạch triển khai Tasks

### Phase 1: Backend — Entity Layer

**Người phụ trách:** \_\_\_\_\_\_\_\_\_\_\_ | **Thời gian:** ~2 ngày

| # | File cần tạo | Chi tiết |
|---|-------------|----------|
| 1.1 | `model/task/TaskStatus.java` | Enum: `TODO, IN_PROGRESS, DONE, REJECTED` |
| 1.2 | `model/task/Priority.java` | Enum: `LOW, MEDIUM, HIGH, URGENT` |
| 1.3 | `model/task/Task.java` | Entity map table `task`. Fields: `id, region (ManyToOne), title, regionType (enum), assistant (ManyToOne User), assignedBy (ManyToOne User), status (enum), priority (enum), description, notes, referenceImageUrl, pageImageUrl, assignedAt, dueDate, createdAt.` Dùng `@PrePersist` set createdAt. |
| 1.4 | `model/task/TaskSubmissionStatus.java` | Enum: `SUBMITTED, APPROVED, REVISION_REQUIRED` |
| 1.5 | `model/task/TaskSubmission.java` | Entity map `task_submission`. Fields: `id, task (ManyToOne), resultImageUrl, fileUrl, note, version, status (enum), submittedAt` |
| 1.6 | `model/task/TaskAttachment.java` | Entity map `task_attachment`. Fields: `id, task (ManyToOne), fileUrl, uploadedAt` |

---

### Phase 2: Backend — Repository Layer

**Người phụ trách:** \_\_\_\_\_\_\_\_\_\_\_ | **Thời gian:** ~0.5 ngày

| # | File | Methods cần có |
|---|------|---------------|
| 2.1 | `repository/task/TaskRepository.java` | `findByAssistantId`, `findByRegionId`, `findByStatus`, `findByAssistantIdAndStatus`, `findByAssignedById`, `countByAssistantIdAndStatus` |
| 2.2 | `repository/task/TaskSubmissionRepository.java` | `findByTaskIdOrderByVersionDesc`, `findByTaskIdAndStatus` |
| 2.3 | `repository/task/TaskAttachmentRepository.java` | `findByTaskId` |

---

### Phase 3: Backend — DTO Layer

**Người phụ trách:** \_\_\_\_\_\_\_\_\_\_\_ | **Thời gian:** ~1 ngày

| # | File | Fields |
|---|------|--------|
| 3.1 | `dto/task/request/TaskRequest.java` | `title, regionType, assistantId, priority, description, notes, referenceImageUrl, dueDate` |
| 3.2 | `dto/task/response/TaskResponse.java` | `id, regionId, regionType, title, assistant (UserBrief), assignedBy (UserBrief), status, priority, description, notes, referenceImageUrl, pageImageUrl, assignedAt, dueDate, createdAt` |
| 3.3 | `dto/task/request/TaskSubmissionRequest.java` | `resultImageUrl, fileUrl, note` |
| 3.4 | `dto/task/response/TaskSubmissionResponse.java` | `id, taskId, resultImageUrl, fileUrl, note, version, status, submittedAt` |
| 3.5 | `dto/task/response/TaskAttachmentResponse.java` | `id, taskId, fileUrl, uploadedAt` |
| 3.6 | `dto/task/request/TaskStatusRequest.java` | `status` |

---

### Phase 4: Backend — Service Layer

**Người phụ trách:** \_\_\_\_\_\_\_\_\_\_\_ | **Thời gian:** ~2 ngày

| # | File | Methods |
|---|------|---------|
| 4.1 | `service/task/TaskService.java` | `getTasks(filters)`, `getTaskById(id)`, `getTasksByRegion(regionId)`, `createTask(request)`, `updateTask(id, request)`, `updateTaskStatus(id, status)`, `deleteTask(id)` |
| 4.2 | `service/task/TaskSubmissionService.java` | `getSubmissions(taskId)`, `submitTask(taskId, request, user)`, `reviewSubmission(submissionId, status)` |
| 4.3 | `service/task/TaskAttachmentService.java` | `addAttachment(taskId, fileUrl)`, `deleteAttachment(id)` |

---

### Phase 5: Backend — Controller Layer

**Người phụ trách:** \_\_\_\_\_\_\_\_\_\_\_ | **Thời gian:** ~1.5 ngày

| # | Method | Endpoint | Role | Mô tả |
|---|--------|----------|------|-------|
| 5.1 | GET | `/api/tasks` | Authenticated | Danh sách tasks (filter: status, assignedTo, priority, page, size) |
| 5.2 | GET | `/api/tasks/{id}` | Authenticated | Chi tiết 1 task kèm submissions & attachments |
| 5.3 | GET | `/api/regions/{regionId}/tasks` | Authenticated | Tasks của 1 region |
| 5.4 | POST | `/api/regions/{regionId}/tasks` | MANGAKA | Tạo task mới, gán cho ASSISTANT |
| 5.5 | PUT | `/api/tasks/{id}` | MANGAKA | Cập nhật task |
| 5.6 | PATCH | `/api/tasks/{id}/status` | MANGAKA / ASSISTANT | Đổi trạng thái task |
| 5.7 | DELETE | `/api/tasks/{id}` | MANGAKA | Xoá task (chỉ khi TODO) |
| 5.8 | GET | `/api/tasks/{taskId}/submissions` | Authenticated | Lịch sử nộp bài theo version |
| 5.9 | POST | `/api/tasks/{taskId}/submissions` | ASSISTANT | Nộp bài, auto increment version |
| 5.10 | PATCH | `/api/submissions/{id}/status` | MANGAKA | Duyệt bài: APPROVED / REVISION_REQUIRED |
| 5.11 | POST | `/api/tasks/{taskId}/attachments` | MANGAKA | Đính kèm file tham khảo |
| 5.12 | DELETE | `/api/attachments/{id}` | MANGAKA | Xoá file đính kèm |

---

### Phase 6: Frontend — API Service

**Người phụ trách:** \_\_\_\_\_\_\_\_\_\_\_ | **Thời gian:** ~1 ngày

| # | Công việc | File | Chi tiết |
|---|-----------|------|----------|
| 6.1 | Tạo service | `src/services/taskService.js` | 11 hàm gọi API tương ứng 12 endpoints ở Phase 5 |
| 6.2 | Update store | `src/app/stores/taskStore.js` | Thay mock data bằng API calls qua taskService |
| 6.3 | Update hooks | `src/shared/hooks/useMockData.js` | Bỏ `useTasks()`, `useTaskSubmissions()`, `useTaskAttachments()` — chuyển sang store |

---

### Phase 7: Frontend — UI Tích hợp

**Người phụ trách:** \_\_\_\_\_\_\_\_\_\_\_ | **Thời gian:** ~2 ngày

| # | Trang/Component | Việc cần làm |
|---|----------------|-------------|
| 7.1 | `TasksPage.jsx` | Thay `useTasks()` mock → `useTaskStore()`, gọi API thật cho filter, detail, approve/revision. Thêm loading & error state |
| 7.2 | `TaskPanel.jsx` | `handleAssign` gọi `createTask API`, `handleSubmit` gọi `submitTask API`, `handleReview` gọi `reviewSubmission API` |
| 7.3 | `ReviewsPage.jsx` | Gọi API thật cho submissions, approve/revision |
| 7.4 | `DashboardPage.jsx` | MangakaDashboard + AssistantDashboard dùng API thật |

---

### Phase 8: Kiểm thử

**Người phụ trách:** \_\_\_\_\_\_\_\_\_\_\_ | **Thời gian:** ~1 ngày

| # | Công việc |
|---|-----------|
| 8.1 | Test backend endpoints với Swagger: create → assign → submit → approve/revision |
| 8.2 | Test frontend với Docker: verify luồng TasksPage → TaskPanel → ReviewsPage |
| 8.3 | Fix bugs phát sinh |
| 8.4 | Merge `feature/tasks` vào `main` |

---

## Chi tiết 12 API Endpoints Tasks

### 1. GET /api/tasks — Danh sách tasks

**Mô tả:** Lấy danh sách tasks với filter & phân trang.

**Query params:** `status, assignedTo, assignedBy, priority, regionId, seriesId, page (default 0), size (default 20)`

**Logic:**
- ASSISTANT → mặc định lọc `assignedTo = currentUserId`
- MANGAKA → mặc định lọc `assignedBy = currentUserId`
- TANTOU_EDITOR / EDITORIAL_BOARD → xem tất cả

**Response:** `Page<TaskResponse>` với pagination metadata.

---

### 2. GET /api/tasks/{id} — Chi tiết task

**Mô tả:** Lấy 1 task kèm submissions + attachments.

**Response:** `TaskResponse` bao gồm mảng `submissions: TaskSubmissionResponse[]` và `attachments: TaskAttachmentResponse[]`.

---

### 3. GET /api/regions/{regionId}/tasks — Tasks của region

**Mô tả:** Lấy tất cả tasks thuộc 1 region (dùng trong workspace).

**Response:** `TaskResponse[]` (không kèm submissions/attachments).

---

### 4. POST /api/regions/{regionId}/tasks — Tạo task

**Mô tả:** MANGAKA tạo task & gán cho ASSISTANT. Role: MANGAKA.

**Validation:**
- `title`: not blank, max 255
- `assistantId`: not null, user phải có role ASSISTANT
- `dueDate`: phải ở tương lai

**Logic:**
1. Validate region tồn tại
2. Validate assistant role
3. Set `assignedBy = currentUser`, `status = TODO`, `assignedAt = now`
4. Copy `pageImageUrl` từ region.page.webImageUrl
5. Lưu task

**Response 201:** `TaskResponse`

---

### 5. PUT /api/tasks/{id} — Cập nhật task

**Mô tả:** Sửa thông tin task. Role: MANGAKA.

**Ràng buộc:** Chỉ sửa được khi status là `TODO` hoặc `REJECTED`.

**Response 200:** `TaskResponse`

---

### 6. PATCH /api/tasks/{id}/status — Đổi trạng thái

**Mô tả:** Chuyển trạng thái task.

**Workflow:**

| Từ | → | Thành | Ai được làm |
|----|---|-------|-------------|
| TODO | → | IN_PROGRESS | ASSISTANT được gán |
| IN_PROGRESS | → | REJECTED | MANGAKA |
| REJECTED | → | IN_PROGRESS | ASSISTANT |

**Response 200:** `TaskResponse` với status mới.

---

### 7. DELETE /api/tasks/{id} — Xoá task

**Mô tả:** Xoá task. Role: MANGAKA. Chỉ xoá khi status = `TODO`.

**Response 204:** No content.

---

### 8. GET /api/tasks/{taskId}/submissions — Lịch sử nộp bài

**Mô tả:** Lấy danh sách các lần nộp bài (theo version giảm dần).

**Response 200:** `TaskSubmissionResponse[]`

---

### 9. POST /api/tasks/{taskId}/submissions — Nộp bài

**Mô tả:** ASSISTANT nộp bài làm. Role: ASSISTANT.

**Validation:**
- Task phải đang `IN_PROGRESS` hoặc `REJECTED`
- Current user phải là task.assistant

**Logic:**
1. Auto increment version (max version + 1)
2. Set `status = SUBMITTED`, `submittedAt = now`

**Response 201:** `TaskSubmissionResponse`

---

### 10. PATCH /api/submissions/{id}/status — Duyệt bài

**Mô tả:** MANGAKA duyệt bài nộp. Role: MANGAKA.

**Logic:**
| Status | Task status sau duyệt |
|--------|----------------------|
| APPROVED | Task → DONE |
| REVISION_REQUIRED | Task → IN_PROGRESS |

**Response 200:** `TaskSubmissionResponse` với status mới.

---

### 11. POST /api/tasks/{taskId}/attachments — Đính kèm file

**Mô tả:** MANGAKA thêm file tham khảo. Role: MANGAKA.

**Response 201:** `TaskAttachmentResponse`

---

### 12. DELETE /api/attachments/{id} — Xoá file đính kèm

**Mô tả:** Xoá file đính kèm. Role: MANGAKA.

**Response 204:** No content.

---

## Luồng hoạt động hoàn chỉnh

```
MANGAKA: Upload page → Vẽ region → Tạo task → Gán ASSISTANT
                                                    ↓
ASSISTANT: Xem task → Nhận (TODO → IN_PROGRESS) → Làm → Nộp bài
                                                    ↓
MANGAKA: Xem bài nộp → Duyệt
                        ├── APPROVED → Task DONE
                        └── REVISION_REQUIRED → Task → IN_PROGRESS → (về ASSISTANT sửa lại)
```

## Gợi ý phân công

| Người | Phase | Công việc | Thời gian |
|-------|-------|-----------|-----------|
| A | Phase 1 + 2 | Entities + Repositories | ~2.5 ngày |
| B | Phase 3 + 4 + 5 | DTOs + Services + Controllers | ~4.5 ngày (phụ thuộc A) |
| C | Phase 6 + 7 | Frontend API Service + UI | ~3 ngày (song song với B) |
| A/B/C | Phase 8 | Kiểm thử | ~1 ngày |

## Các branch dự kiến

| Branch | Mục đích |
|--------|----------|
| `feature/task-entities` | Phase 1 + 2 |
| `feature/task-backend` | Phase 3 + 4 + 5 |
| `feature/task-frontend` | Phase 6 + 7 |
| `feature/tasks` | Merge tất cả, test, release |
