# Page API — `/api/v1`

Base URL: `http://localhost:8080/api/v1`

> 🔐 Tất cả API yêu cầu **Bearer token**.

---

## 📦 Models

### PageResponse
```json
{
  "id": "number",
  "chapterId": "number",
  "pageNumber": "number",
  "originalImageUrl": "string (URL ảnh gốc full size — dùng trong workspace)",
  "webImageUrl": "string (URL ảnh resize 1920px — hiển thị web)",
  "thumbnailUrl": "string (URL ảnh thumbnail 320px — danh sách)",
  "publicId": "string (Cloudinary public ID — debug)",
  "width": "number (px)",
  "height": "number (px)",
  "status": "string (UPLOADED | REGIONS_DEFINED | IN_PRODUCTION | COMPLETED)",
  "createdAt": "ISO datetime"
}
```

### PageReorderRequest
```json
{
  "pageNumber": 3
}
```

| Field      | Type   | Required | Mô tả |
|------------|--------|----------|-------|
| pageNumber | number | ✅       | Số thứ tự mới (bắt đầu từ 1) |

### PageBatchReorderRequest
```json
{
  "pageIds": [30, 10, 20]
}
```

| Field   | Type           | Required | Mô tả |
|---------|----------------|----------|-------|
| pageIds | array[number]  | ✅       | Danh sách page IDs theo thứ tự mới. Phần tử đầu = pageNumber 1, phần tử thứ 2 = pageNumber 2, ... |

---

## 1. GET PAGES — Danh sách pages của chapter

```
GET /api/v1/chapters/{chapterId}/pages
Authorization: Bearer <token>
```

> 🔒 Yêu cầu: **isAuthenticated()** — tất cả user đã đăng nhập

| Param     | Type   | Required | Mô tả |
|-----------|--------|----------|-------|
| chapterId | number | ✅       | ID của chapter (VD: 1) |

**Response 200:**
```json
[
  {
    "id": 1,
    "chapterId": 1,
    "pageNumber": 1,
    "originalImageUrl": "https://res.cloudinary.com/.../v1234/manga_studio/u3/s0/ch1/p1.jpg",
    "webImageUrl": "https://res.cloudinary.com/.../c_limit,w_1920/v1234/manga_studio/u3/s0/ch1/p1.jpg",
    "thumbnailUrl": "https://res.cloudinary.com/.../c_limit,w_320/v1234/manga_studio/u3/s0/ch1/p1.jpg",
    "publicId": "manga_studio/u3/s0/ch1/p1",
    "width": 1200,
    "height": 1800,
    "status": "UPLOADED",
    "createdAt": "2026-05-29T10:00:00"
  }
]
```

---

## 2. UPLOAD 1 PAGE — Upload 1 file ảnh

```
POST /api/v1/chapters/{chapterId}/pages
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

> 🔒 Yêu cầu: **hasRole('MANGAKA')**

| Field      | Type              | Required | Mô tả |
|------------|-------------------|----------|-------|
| chapterId  | path param        | ✅       | ID của chapter |
| file       | file (multipart)  | ✅       | File ảnh (jpg, png, ...) |
| pageNumber | number (form)     | ✅       | Số thứ tự page (VD: 5 → page thứ 5) |

**Response 201:**
```json
{
  "id": 4,
  "chapterId": 1,
  "pageNumber": 4,
  "originalImageUrl": "https://res.cloudinary.com/...",
  "webImageUrl": "https://res.cloudinary.com/...",
  "thumbnailUrl": "https://res.cloudinary.com/...",
  "publicId": "manga_studio/u3/s0/ch1/p4",
  "width": 4200,
  "height": 6000,
  "status": "UPLOADED",
  "createdAt": "2026-05-29T22:00:00"
}
```

| Error | Status | Mô tả |
|-------|--------|-------|
| Page number already exists | 400 | Số thứ tự đã có page khác dùng |

---

## 3. UPLOAD BATCH — Import nhiều file cùng lúc

```
POST /api/v1/chapters/{chapterId}/pages/batch
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

> 🔒 Yêu cầu: **hasRole('MANGAKA')**

| Field     | Type                   | Required | Mô tả |
|-----------|------------------------|----------|-------|
| chapterId | path param             | ✅       | ID của chapter |
| files     | file[] (multipart)     | ✅       | Chọn nhiều file cùng lúc (input[type=file][multiple]) |

> 💡 **Auto-assign pageNumber:** Backend tự động gán pageNumber dựa trên max hiện tại.
> - Nếu chapter đã có page 1,2,3 → upload 2 file → file đầu = 4, file sau = 5
> - Nếu chapter chưa có page nào → bắt đầu từ 1
> - Muốn sắp xếp → dùng Reorder API sau khi upload

**Response 201:**
```json
[
  {
    "id": 4,
    "chapterId": 1,
    "pageNumber": 4,
    "originalImageUrl": "...",
    "webImageUrl": "...",
    "thumbnailUrl": "...",
    "publicId": "manga_studio/u3/s0/ch1/p4",
    "width": 4200,
    "height": 6000,
    "status": "UPLOADED",
    "createdAt": "2026-05-29T22:00:00"
  },
  {
    "id": 5,
    "chapterId": 1,
    "pageNumber": 5,
    "originalImageUrl": "...",
    "webImageUrl": "...",
    "thumbnailUrl": "...",
    "publicId": "manga_studio/u3/s0/ch1/p5",
    "width": 3840,
    "height": 5400,
    "status": "UPLOADED",
    "createdAt": "2026-05-29T22:00:00"
  }
]
```

---

## 4. DELETE PAGE — Xoá 1 page

```
DELETE /api/v1/pages/{id}
Authorization: Bearer <token>
```

> 🔒 Yêu cầu: **hasRole('MANGAKA')**

> 💡 Xoá đồng thời ảnh trên Cloudinary + record trong database.

| Param | Type   | Required | Mô tả |
|-------|--------|----------|-------|
| id    | number | ✅       | ID của page cần xoá |

**Response 204:** (No Content)

| Error | Status | Mô tả |
|-------|--------|-------|
| Page not found | 404 | Không tìm thấy page |

---

## 5. UPDATE ORDER — Đổi số thứ tự 1 page

```
PUT /api/v1/pages/{id}/order
Authorization: Bearer <token>
Content-Type: application/json
```

> 🔒 Yêu cầu: **hasRole('MANGAKA')**

| Param | Type   | Required | Mô tả |
|-------|--------|----------|-------|
| id    | number | ✅       | ID của page cần đổi số |

**Request Body (JSON):**
```json
{
  "pageNumber": 3
}
```

> 💡 Nếu số mới đã có page khác, backend tự động đẩy các page khác lên/xuống.
>
> VD: Chapter có `[page10-số1, page20-số2, page30-số3]`
> → Đổi page10 từ số 1 sang số 3
> → Kết quả: `[page20-số1, page30-số2, page10-số3]`

**Response 200:** (PageResponse — page đã cập nhật)

| Error | Status | Mô tả |
|-------|--------|-------|
| Page not found | 404 | Không tìm thấy page |

---

## 6. REORDER PAGES — Batch reorder (kéo thả)

```
PUT /api/v1/chapters/{chapterId}/pages/reorder
Authorization: Bearer <token>
Content-Type: application/json
```

> 🔒 Yêu cầu: **hasRole('MANGAKA')**

| Param     | Type   | Required | Mô tả |
|-----------|--------|----------|-------|
| chapterId | number | ✅       | ID của chapter |

**Request Body (JSON):**
```json
{
  "pageIds": [30, 10, 20]
}
```

> 💡 **Cách hoạt động:**
> 1. Frontend có danh sách pages theo thứ tự cũ: `p1(id=10), p2(id=20), p3(id=30)`
> 2. Người dùng kéo thả p3 lên đầu: `p3(id=30), p1(id=10), p2(id=20)`
> 3. Frontend gửi: `{ "pageIds": [30, 10, 20] }`
> 4. Backend cập nhật: page(30).pageNumber=1, page(10).pageNumber=2, page(20).pageNumber=3

**Response 200:**
```json
[
  {
    "id": 30,
    "chapterId": 1,
    "pageNumber": 1,
    "...": "..."
  },
  {
    "id": 10,
    "chapterId": 1,
    "pageNumber": 2,
    "...": "..."
  },
  {
    "id": 20,
    "chapterId": 1,
    "pageNumber": 3,
    "...": "..."
  }
]
```

| Error | Status | Mô tả |
|-------|--------|-------|
| Invalid page count. Expected X but got Y | 400 | Số lượng pageIds không khớp tổng pages trong chapter |
| Page with id X not found in chapter  | 400 | Page ID không thuộc chapter này |

---

## 🔐 Role Permissions Summary

| Endpoint                                    | MANGAKA | ASSISTANT | TANTOU_EDITOR | EDITORIAL_BOARD |
|---------------------------------------------|:-------:|:---------:|:-------------:|:---------------:|
| GET /chapters/{id}/pages                    | ✅      | ✅        | ✅            | ✅              |
| POST /chapters/{id}/pages                   | ✅      | ❌        | ❌            | ❌              |
| POST /chapters/{id}/pages/batch             | ✅      | ❌        | ❌            | ❌              |
| DELETE /pages/{id}                          | ✅      | ❌        | ❌            | ❌              |
| PUT /pages/{id}/order                       | ✅      | ❌        | ❌            | ❌              |
| PUT /chapters/{id}/pages/reorder            | ✅      | ❌        | ❌            | ❌              |

---

## 📁 Cloudinary Storage Structure

```
manga_studio/
  └── u{userId}/              ← thư mục tác giả (VD: u3 = user id 3)
       └── s{seriesId}/       ← thư mục series (VD: s0 = series id 0, hiện hardcode)
            └── ch{chapterId}/  ← thư mục chapter (VD: ch1 = chapter id 1)
                 ├── p1.jpg    ← page 1
                 ├── p2.jpg    ← page 2
                 └── ...
```

Mỗi ảnh có 3 URL:
| URL            | Transform | Mục đích |
|----------------|-----------|----------|
| originalUrl    | (none)    | Ảnh gốc full size — workspace |
| webImageUrl    | w_1920    | Resize 1920px — hiển thị web |
| thumbnailUrl   | w_320     | Thumbnail 320px — danh sách |

> ⚠️ **Lưu ý:** `getSeriesIdFromChapter()` hiện hardcode `0L` vì chưa có Chapter entity.
> Cần cập nhật sau khi có Chapter module.
