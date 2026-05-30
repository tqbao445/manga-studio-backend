# Series API — `/api/series`

Base URL: `http://localhost:8080/api/series`

> 🔐 Tất cả API yêu cầu **Bearer token** (trừ khi có ghi chú riêng).

---

## 📖 Models

### Enums

**Genre** (thể loại):
```
ACTION | FANTASY | ROMANCE | COMEDY | DRAMA
```

**TargetDemographic** (đối tượng độc giả):
```
SHONEN (nam thiếu niên) | SHOJO (nữ thiếu niên) | SEINEN (nam trưởng thành) | JOSEI (nữ trưởng thành)
```

**SeriesStatus** (trạng thái):
```
DRAFT → PENDING_APPROVAL → ONGOING → HIATUS / CANCELLED / COMPLETED
                            ↕                      ↕
                        AT_RISK              (terminal states)
```

**SeriesSortBy** (sort options):
```
UPDATED_AT_DESC (mặc định) | UPDATED_AT_ASC | CREATED_AT_DESC | CREATED_AT_ASC | TITLE_ASC | TITLE_DESC
```

### SeriesResponse
```json
{
  "id": "number",
  "title": "string",
  "titleJp": "string | null",
  "synopsis": "string | null",
  "genre": "string (ACTION | FANTASY | ...)",
  "targetDemographic": "string (SHONEN | SHOJO | ...)",
  "status": "string (DRAFT | ONGOING | ...)",
  "coverColor": "string | null (#e63946)",
  "coverImageUrl": "string | null",
  "isMature": "boolean",
  "mangaka": { "UserDTO" },
  "tantouEditor": { "UserDTO" } | null,
  "chapterCount": "number | null",
  "currentRank": "number | null",
  "currentTier": "string | null (SILVER | GOLD | BRONZE)",
  "createdAt": "ISO datetime",
  "updatedAt": "ISO datetime"
}
```

---

## 1. GET ALL — Danh sách series (có filter + phân trang)

```
GET /api/series?status=ONGOING&genre=ACTION&targetDemographic=SHONEN&search=dragon&page=0&size=20&sort=UPDATED_AT_DESC
Authorization: Bearer <token>
```

> 🔒 Yêu cầu: **isAuthenticated()**

| Param             | Type   | Required | Default           | Mô tả |
|-------------------|--------|----------|-------------------|-------|
| status            | string | ❌       | -                 | Lọc theo trạng thái |
| genre             | string | ❌       | -                 | Lọc theo thể loại |
| targetDemographic | string | ❌       | -                 | Lọc theo đối tượng |
| search            | string | ❌       | -                 | Tìm theo title (LIKE %search%) |
| page              | number | ❌       | 0                 | Trang số (0-indexed) |
| size              | number | ❌       | 20                | Số item mỗi trang |
| sort              | string | ❌       | UPDATED_AT_DESC   | Cách sắp xếp |

**Response 200:**
```json
{
  "content": [
    {
      "id": 3,
      "title": "Phantom Thief K",
      "titleJp": "怪盗K",
      "genre": "ACTION",
      "targetDemographic": "SHONEN",
      "status": "ONGOING",
      "mangaka": { "id": 1, "displayName": "Ichikawa", "role": "MANGAKA" },
      "tantouEditor": { "id": 5, "displayName": "Sato", "role": "TANTOU_EDITOR" },
      "chapterCount": 24,
      "currentRank": 128,
      "currentTier": "SILVER"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

---

## 2. GET BY ID — Chi tiết 1 series

```
GET /api/series/{id}
Authorization: Bearer <token>
```

> 🔒 Yêu cầu: **isAuthenticated()**

| Param | Type   | Required | Mô tả |
|-------|--------|----------|-------|
| id    | number | ✅       | ID của series |

**Response 200:** (SeriesResponse — giống mẫu ở trên)

| Error | Status | Mô tả |
|-------|--------|-------|
| Series not found | 404 | Không tìm thấy series |

---

## 3. CREATE — Tạo series mới

```
POST /api/series
Authorization: Bearer <token>
```

> 🔒 Yêu cầu: **hasRole('MANGAKA')** — chỉ MANGAKA

**Request Body (JSON):**
```json
{
  "title": "New Manga",
  "titleJp": "新しい漫画",
  "synopsis": "A story about something amazing.",
  "genre": "ACTION",
  "targetDemographic": "SHONEN",
  "coverColor": "#e63946",
  "coverImageUrl": null,
  "isMature": false
}
```

| Field              | Type    | Required | Mô tả |
|--------------------|---------|----------|-------|
| title              | string  | ✅       | Tên series |
| titleJp            | string  | ❌       | Tên gốc tiếng Nhật |
| synopsis           | string  | ❌       | Tóm tắt nội dung |
| genre              | string  | ✅       | Thể loại (ACTION, FANTASY, ...) |
| targetDemographic  | string  | ✅       | Đối tượng độc giả |
| coverColor         | string  | ❌       | Màu nền card (#hex) |
| coverImageUrl      | string  | ❌       | URL ảnh bìa |
| isMature           | boolean | ❌       | Có nội dung người lớn không |

**Response 201:** (SeriesResponse — status tự động = `DRAFT`)

---

## 4. UPDATE — Cập nhật series

```
PUT /api/series/{id}
Authorization: Bearer <token>
```

> 🔒 Yêu cầu: **hasRole('MANGAKA')** + chỉ chủ sở hữu mới update được

> 💡 **Null-safe update:** Chỉ gửi field muốn đổi, field null = giữ nguyên.
> Không thể đổi: status, mangaka, tantouEditor, chapterCount, currentRank, currentTier.

**Request Body (JSON):** (cùng cấu trúc với CREATE)

**Response 200:** (SeriesResponse)

| Error | Status | Mô tả |
|-------|--------|-------|
| Series not found or not owned by you | 404 | Không có quyền sửa |

---

## 5. DELETE — Xoá series

```
DELETE /api/series/{id}
Authorization: Bearer <token>
```

> 🔒 Yêu cầu: **hasRole('MANGAKA')** + chỉ chủ sở hữu + chỉ xoá được `DRAFT`

**Response 204:** (No Content — không có body)

| Error | Status | Mô tả |
|-------|--------|-------|
| Series not found or not owned by you | 404 | Không tìm thấy hoặc không phải chủ |
| Only DRAFT series can be deleted | 400 | Series không ở trạng thái DRAFT |

---

## 6. SUBMIT — Gửi duyệt

```
POST /api/series/{id}/submit
Authorization: Bearer <token>
```

> 🔒 Yêu cầu: **hasRole('MANGAKA')** + chỉ chủ sở hữu

**State transition:** `DRAFT → PENDING_APPROVAL`

**Response 200:** (SeriesResponse với status = `PENDING_APPROVAL`)

| Error | Status | Mô tả |
|-------|--------|-------|
| Only DRAFT series can be submitted | 400 | Không submit được |

---

## 7. APPROVE — Phê duyệt series

```
POST /api/series/{id}/approve
Authorization: Bearer <token>
```

> 🔒 Yêu cầu: **hasRole('EDITORIAL_BOARD')**

**State transition:** `PENDING_APPROVAL → ONGOING`

**Request Body (JSON):**
```json
{
  "tantouEditorId": 5,
  "notes": "Approved, looks promising!"
}
```

| Field           | Type   | Required | Mô tả |
|-----------------|--------|----------|-------|
| tantouEditorId  | number | ❌       | ID của biên tập viên phụ trách |
| notes           | string | ❌       | Ghi chú (chưa dùng, dự phòng) |

**Response 200:** (SeriesResponse với status = `ONGOING`)

| Error | Status | Mô tả |
|-------|--------|-------|
| Series is not pending approval | 400 | Không approve được |
| Editor not found | 404 | Editor ID không tồn tại |

---

## 8. REJECT — Từ chối series

```
POST /api/series/{id}/reject
Authorization: Bearer <token>
```

> 🔒 Yêu cầu: **hasRole('EDITORIAL_BOARD')**

**State transition:** `PENDING_APPROVAL → DRAFT`

**Request Body (JSON):**
```json
{
  "notes": "Needs more character development"
}
```

| Field | Type   | Required | Mô tả |
|-------|--------|----------|-------|
| notes | string | ❌       | Lý do từ chối (dự phòng) |

**Response 200:** (SeriesResponse với status = `DRAFT`)

| Error | Status | Mô tả |
|-------|--------|-------|
| Series is not pending approval | 400 | Không reject được |

---

## 9. UPDATE STATUS — Chuyển trạng thái

```
PATCH /api/series/{id}/status
Authorization: Bearer <token>
```

> 🔒 Yêu cầu: **hasRole('EDITORIAL_BOARD')**

**State machine diagram:**
```
ONGOING → HIATUS | AT_RISK | CANCELLED | COMPLETED
HIATUS  → ONGOING | CANCELLED
AT_RISK → ONGOING | CANCELLED
```

**Request Body (JSON):**
```json
{
  "status": "HIATUS"
}
```

| Field  | Type   | Required | Mô tả |
|--------|--------|----------|-------|
| status | string | ✅       | Trạng thái mới |

**Response 200:** (SeriesResponse với status mới)

| Error | Status | Mô tả |
|-------|--------|-------|
| Cannot transition from X to Y | 400 | Transition không hợp lệ |

---

## 🔐 Role Permissions Summary

| Endpoint                     | MANGAKA | TANTOU_EDITOR | EDITORIAL_BOARD | ASSISTANT |
|------------------------------|:-------:|:-------------:|:---------------:|:---------:|
| GET /api/series              | ✅      | ✅            | ✅              | ✅        |
| GET /api/series/{id}         | ✅      | ✅            | ✅              | ✅        |
| POST /api/series             | ✅      | ❌            | ❌              | ❌        |
| PUT /api/series/{id}         | ✅      | ❌            | ❌              | ❌        |
| DELETE /api/series/{id}      | ✅      | ❌            | ❌              | ❌        |
| POST /submit                 | ✅      | ❌            | ❌              | ❌        |
| POST /approve                | ❌      | ❌            | ✅              | ❌        |
| POST /reject                 | ❌      | ❌            | ✅              | ❌        |
| PATCH /status                | ❌      | ❌            | ✅              | ❌        |
