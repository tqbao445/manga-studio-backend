# Voting & Publish Workflow cho Series

## Tổng quan

Thay thế cơ chế duyệt series 1 người bằng cơ chế **bỏ phiếu** của Editorial Board,
kết hợp với **hẹn lịch publish tự động** và **auto-assign TANTOU_EDITOR theo thể loại**.

---

## Role system

```java
public enum Role {
    MANGAKA,
    ASSISTANT,
    TANTOU_EDITOR,      // biên tập viên, có specialties (thể loại phụ trách)
    EDITORIAL_BOARD,    // hội đồng biên tập, được bỏ phiếu
    CHIEF_EDITOR        // trưởng ban, quyền cao nhất (thêm mới)
}
```

### Phân quyền chi tiết

| Hành động | Role thực hiện |
|-----------|----------------|
| Submit series (DRAFT → PENDING_APPROVAL) | MANGAKA |
| Bỏ phiếu APPROVE / REJECT | CHIEF_EDITOR + EDITORIAL_BOARD |
| Mở / kết thúc vote | CHIEF_EDITOR |
| Veto khi vote 50-50 | CHIEF_EDITOR |
| Gán TANTOU_EDITOR | **Auto** (theo genre) — CHIEF_EDITOR có thể override |
| Đặt lịch publish | CHIEF_EDITOR |
| Publish ngay | CHIEF_EDITOR |
| Chuyển trạng thái sau publish (HIATUS, CANCELLED...) | CHIEF_EDITOR + EDITORIAL_BOARD |

---

## Entity & Model

### 1. `Series.java` — Thêm field

```java
private LocalDateTime scheduledPublishAt;  // ngày giờ hẹn publish, nullable
```

### 2. `VoteType.java` — Enum mới

```java
public enum VoteType {
    APPROVE,
    REJECT
}
```

### 3. `SeriesVote.java` — Entity mới

Bảng `series_votes`:

| Column | Type | Constraints |
|--------|------|-------------|
| id | Long | PK, auto-increment |
| series_id | Long | FK → series, NOT NULL |
| voter_id | Long | FK → users, NOT NULL |
| vote | VoteType (String) | NOT NULL (APPROVE / REJECT) |
| notes | String | nullable |
| created_at | LocalDateTime | |
| updated_at | LocalDateTime | |

**Unique constraint:** `(series_id, voter_id)` — mỗi người chỉ vote 1 lần / series

### 4. `EditorSpecialty.java` — Entity mới

Bảng `editor_specialties` — lưu thể loại mà TANTOU_EDITOR phụ trách:

| Column | Type | Constraints |
|--------|------|-------------|
| id | Long | PK, auto-increment |
| editor_id | Long | FK → users (role = TANTOU_EDITOR), NOT NULL |
| genre | String | NOT NULL (ACTION, FANTASY, ROMANCE, ...) |
| created_at | LocalDateTime | |
| updated_at | LocalDateTime | |

**Unique constraint:** `(editor_id, genre)` — 1 editor không trùng genre

---

## DTOs

### `VoteRequest.java`

```json
{
  "vote": "APPROVE",
  "notes": "Great story!"
}
```

| Field | Type | Required | Mô tả |
|-------|------|----------|-------|
| vote | VoteType | ✅ | APPROVE hoặc REJECT |
| notes | String | ❌ | Ghi chú |

### `VoteResponse.java`

```json
{
  "seriesId": 1,
  "voteCountApprove": 2,
  "voteCountReject": 1,
  "totalBoardMembers": 3,
  "currentStatus": "APPROVED",
  "myVote": "APPROVE"
}
```

### `SchedulePublishRequest.java`

```json
{
  "scheduledPublishAt": "2026-06-15T10:00:00"
}
```

| Field | Type | Required | Mô tả |
|-------|------|----------|-------|
| scheduledPublishAt | LocalDateTime (ISO) | ✅ | Ngày giờ publish |

---

## Repositories

### `SeriesVoteRepository.java` — Mới

```java
List<SeriesVote> findBySeriesId(Long seriesId);
Optional<SeriesVote> findBySeriesIdAndVoterId(Long seriesId, Long voterId);
long countBySeriesIdAndVote(Long seriesId, VoteType vote);
void deleteBySeriesId(Long seriesId);
```

### `EditorSpecialtyRepository.java` — Mới

```java
List<User> findEditorsByGenre(Genre genre);
// JOIN editor_specialties + users để lấy danh sách TANTOU_EDITOR phụ trách genre đó
boolean existsByEditorIdAndGenre(Long editorId, Genre genre);
```

### `UserRepository.java` — Thêm method

```java
List<User> findByRole(Role role);
long countByRole(Role role);
```

### `SeriesRepository.java` — Thêm method

```java
List<Series> findByStatusAndScheduledPublishAtBefore(SeriesStatus status, LocalDateTime time);
int countByTantouEditorAndStatus(User editor, SeriesStatus status);
```

---

## Service Layer

### `SeriesWorkflowService.java` — Sửa đổi

**Xoá:**
- `approve(Long id, ApproveRequest, CustomUserDetails)` — single approve
- `reject(Long id, RejectRequest, CustomUserDetails)` — single reject

**Thêm:**

#### `castVote(Long seriesId, VoteRequest request, CustomUserDetails user)`

```
1. Tìm series → phải PENDING_APPROVAL, nếu không → 400
2. Kiểm tra user có role EDITORIAL_BOARD hoặc CHIEF_EDITOR
3. Upsert vote (INSERT nếu chưa, UPDATE nếu đã vote)
4. Đếm total EDITORIAL_BOARD + CHIEF_EDITOR members
5. Đếm APPROVE votes: seriesVoteRepository.countBySeriesIdAndVote(id, APPROVE)
6. Đếm REJECT votes: seriesVoteRepository.countBySeriesIdAndVote(id, REJECT)
7. Nếu APPROVE > total/2:
   - Auto-assign TANTOU_EDITOR (logic theo genre)
   - series.setStatus(APPROVED)
   - series.setScheduledPublishAt(null)
8. Nếu REJECT > total/2:
   - series.setStatus(DRAFT)
   - seriesVoteRepository.deleteBySeriesId(id)
9. Return VoteResponse
```

#### `autoAssignEditor(Series series)` — private helper

```
1. Lấy series.genre
2. EditorSpecialtyRepository.findEditorsByGenre(genre)
3. Nếu có editor phù hợp:
   - Trong danh sách đó, chọn editor có ít series ONGOING nhất
   - series.setTantouEditor(editor)
4. Nếu không có editor nào phù hợp genre:
   - Fallback: tìm TANTOU_EDITOR bất kỳ có ít series ONGOING nhất
   - series.setTantouEditor(editor)
```

#### `getVoteResults(Long seriesId)`

```
1. Tìm series
2. Đếm APPROVE, REJECT votes + total board members
3. Return VoteResponse (không modify state)
```

#### `schedulePublish(Long seriesId, SchedulePublishRequest request, CustomUserDetails user)`

```
1. Tìm series → phải APPROVED, nếu không → 400
2. Kiểm tra user là CHIEF_EDITOR
3. series.setScheduledPublishAt(request.getScheduledPublishAt())
4. Return SeriesResponse
```

#### `publishNow(Long seriesId, CustomUserDetails user)`

```
1. Tìm series → phải APPROVED, nếu không → 400
2. Kiểm tra user là CHIEF_EDITOR
3. series.setStatus(ONGOING)
4. series.setScheduledPublishAt(null)
5. Return SeriesResponse
```

#### `overrideEditor(Long seriesId, Long editorId, CustomUserDetails user)`

```
1. Tìm series → phải APPROVED, nếu không → 400
2. Kiểm tra user là CHIEF_EDITOR
3. Tìm editor (role TANTOU_EDITOR)
4. series.setTantouEditor(editor)
5. Return SeriesResponse
```

---

## Controller

### `SeriesController.java` — Sửa endpoints

| Method | Endpoint | Role | Mô tả |
|--------|----------|------|-------|
| ❌ Xoá | `POST /{id}/approve` | — | — |
| ❌ Xoá | `POST /{id}/reject` | — | — |
| ✅ Thêm | `POST /{id}/vote` | EDITORIAL_BOARD, CHIEF_EDITOR | Bỏ phiếu |
| ✅ Thêm | `GET /{id}/votes` | isAuthenticated | Xem kết quả vote |
| ✅ Thêm | `POST /{id}/schedule-publish` | CHIEF_EDITOR | Đặt lịch publish |
| ✅ Thêm | `POST /{id}/publish` | CHIEF_EDITOR | Publish ngay |
| ✅ Thêm | `POST /{id}/override-editor` | CHIEF_EDITOR | Override gán editor |

Giữ nguyên:
- `POST /{id}/submit` — MANGAKA
- `PATCH /{id}/status` — EDITORIAL_BOARD, CHIEF_EDITOR

---

## Scheduled Task — Auto Publish

### `MangaStudioApplication.java`

```java
@EnableScheduling
```

### `SeriesPublishScheduler.java` — Class mới

```java
@Component
@RequiredArgsConstructor
public class SeriesPublishScheduler {

    private final SeriesRepository seriesRepository;

    @Scheduled(fixedDelay = 30000)  // chạy mỗi 30 giây
    @Transactional
    public void autoPublishScheduledSeries() {
        List<Series> seriesList = seriesRepository
            .findByStatusAndScheduledPublishAtBefore(
                SeriesStatus.APPROVED, LocalDateTime.now());

        for (Series series : seriesList) {
            series.setStatus(SeriesStatus.ONGOING);
            series.setScheduledPublishAt(null);
        }
    }
}
```

---

## Auto Warning System — Cảnh báo tự động

Hệ thống tự động phát hiện series ONGOING có dấu hiệu giảm tương tác và chuyển sang AT_RISK.

### Ngưỡng cảnh báo

- **View / tương tác tháng này giảm > 50% so với tháng trước**
- Dữ liệu view được import từ Excel vào bảng `series_metrics`

### Entity mới: `SeriesMetric.java`

Bảng `series_metrics`:

| Column | Type | Constraints |
|--------|------|-------------|
| id | Long | PK, auto-increment |
| series_id | Long | FK → series, NOT NULL |
| month | YearMonth (String) | NOT NULL (VD: "2026-05") |
| total_views | Long | NOT NULL |
| total_interactions | Long | nullable |
| created_at | LocalDateTime | |
| updated_at | LocalDateTime | |

**Unique constraint:** `(series_id, month)` — mỗi series 1 bản ghi / tháng

### Repository mới: `SeriesMetricRepository.java`

```java
Optional<SeriesMetric> findBySeriesIdAndMonth(Long seriesId, YearMonth month);
// Lấy metric tháng gần nhất
```

### Scheduler mới: `SeriesWarningScheduler.java`

```java
@Component
@RequiredArgsConstructor
public class SeriesWarningScheduler {

    private final SeriesRepository seriesRepository;
    private final SeriesMetricRepository metricRepository;

    @Scheduled(cron = "0 0 6 * * ?")  // chạy 6h sáng mỗi ngày
    @Transactional
    public void autoWarningCheck() {
        List<Series> ongoingSeries = seriesRepository.findByStatus(SeriesStatus.ONGOING);
        YearMonth thisMonth = YearMonth.now();
        YearMonth lastMonth = thisMonth.minusMonths(1);

        for (Series series : ongoingSeries) {
            // Lấy view tháng này và tháng trước
            Optional<SeriesMetric> thisMetric = metricRepository.findBySeriesIdAndMonth(series.getId(), thisMonth);
            Optional<SeriesMetric> lastMetric = metricRepository.findBySeriesIdAndMonth(series.getId(), lastMonth);

            if (thisMetric.isEmpty() || lastMetric.isEmpty()) continue;

            long thisViews = thisMetric.get().getTotalViews();
            long lastViews = lastMetric.get().getTotalViews();

            if (lastViews > 0 && thisViews < lastViews / 2) {
                // Giảm > 50% → auto chuyển AT_RISK
                series.setStatus(SeriesStatus.AT_RISK);
            }
        }
    }
}
```

**Lưu ý:** KHÔNG auto CANCELLED — CHIEF_EDITOR quyết định sau khi series đã ở AT_RISK.

### Import dữ liệu từ Excel

Sẽ có endpoint hoặc batch job riêng để import file Excel vào bảng `series_metrics`.
Chi tiết implement sau.

---

## State Machine (Final)

```
DRAFT
  │
  │ submitForApproval() [MANGAKA]
  ▼
PENDING_APPROVAL
  │
  │ castVote() [EDITORIAL_BOARD / CHIEF_EDITOR]
  │
  ┌────┴────┐
  │         │
APPROVED   DRAFT (nếu REJECT > 50%)
  │                 (reset votes)
  │
  │ — Auto-assign TANTOU_EDITOR (theo genre)
  │
  │ ┌────────────────────────────────────┐
  │ │ schedulePublish() [CHIEF_EDITOR]   │ → set scheduledPublishAt
  │ │ publishNow() [CHIEF_EDITOR]        │ → ONGOING ngay
  │ │ auto (scheduler, 30s)              │ → ONGOING khi đến giờ
  │ │ overrideEditor() [CHIEF_EDITOR]    │ → gán lại editor khác
  ▼ ▼
ONGOING
  │
  ├──→ HIATUS (updateStatus) [CHIEF_EDITOR / EDITORIAL_BOARD]
  ├──→ AT_RISK (auto — khi view giảm >50%) [SYSTEM]
  │          └──→ ONGOING (updateStatus) [CHIEF_EDITOR / EDITORIAL_BOARD]
  │          └──→ CANCELLED (updateStatus) [CHIEF_EDITOR / EDITORIAL_BOARD]
  ├──→ CANCELLED (updateStatus) [CHIEF_EDITOR / EDITORIAL_BOARD]
  └──→ COMPLETED (updateStatus) [CHIEF_EDITOR / EDITORIAL_BOARD]
```

---

## Danh sách files

### Files cần tạo mới

| # | Path | Mô tả |
|---|------|-------|
| 1 | `model/auth/VoteType.java` | Enum APPROVE / REJECT |
| 2 | `model/series/SeriesVote.java` | Entity series_votes |
| 3 | `model/auth/EditorSpecialty.java` | Entity editor_specialties |
| 4 | `repository/series/SeriesVoteRepository.java` | CRUD votes |
| 5 | `repository/auth/EditorSpecialtyRepository.java` | Tìm editor theo genre |
| 6 | `dto/series/request/VoteRequest.java` | Request vote |
| 7 | `dto/series/response/VoteResponse.java` | Response kết quả vote |
| 8 | `dto/series/request/SchedulePublishRequest.java` | Request đặt lịch |
| 9 | `service/series/SeriesPublishScheduler.java` | Scheduled task auto publish |
| 10 | `model/series/SeriesMetric.java` | Entity series_metrics (dữ liệu view Excel) |
| 11 | `repository/series/SeriesMetricRepository.java` | CRUD metrics |
| 12 | `service/series/SeriesWarningScheduler.java` | Scheduled task auto warning AT_RISK |

### Files cần sửa

| # | Path | Thay đổi |
|---|------|----------|
| 1 | `model/auth/Role.java` | + CHIEF_EDITOR |
| 2 | `model/series/Series.java` | + field scheduledPublishAt |
| 3 | `model/auth/User.java` | + Set<EditorSpecialty> specialties (OneToMany) |
| 4 | `repository/auth/UserRepository.java` | + findByRole(), countByRole() |
| 5 | `repository/series/SeriesRepository.java` | + findByStatusAndScheduledPublishAtBefore(), countByTantouEditorAndStatus() |
| 6 | `service/series/SeriesWorkflowService.java` | Xoá approve/reject, thêm castVote/getVoteResults/schedulePublish/publishNow/overrideEditor + autoAssignEditor |
| 7 | `controller/series/SeriesController.java` | Xoá approve/reject endpoints, thêm vote/votes/schedule-publish/publish/override-editor |
| 8 | `MangaStudioApplication.java` | + @EnableScheduling |
| 9 | `data/DataSeeder.java` | Seed thêm CHIEF_EDITOR + EditorSpecialty cho TANTOU_EDITOR |
| 10 | `docs/api/series.md` | Cập nhật endpoints |

---

## Lưu ý

- Khi REJECT > 50%: xoá toàn bộ votes của series đó (reset để lần sau vote lại)
- Upsert vote: cho phép EDITORIAL_BOARD đổi ý (gọi lại API vote)
- Auto-assign TANTOU_EDITOR: ưu tiên match genre → fallback editor ít việc nhất
- CHIEF_EDITOR có thể override editor bất kỳ lúc nào (khi series đang APPROVED)
- Auto publish scheduler chạy 30s/lần, độ trễ publish tối đa 30s
- Auto warning scheduler chạy 6h sáng mỗi ngày, kiểm tra view tháng này vs tháng trước
- KHÔNG auto CANCELLED — CHIEF_EDITOR quyết định sau khi series đã ở AT_RISK
- Dữ liệu view import từ Excel vào bảng `series_metrics` (implement sau)
