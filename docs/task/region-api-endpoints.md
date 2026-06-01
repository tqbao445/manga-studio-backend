# Region API Endpoints — Chi tiết

## Bảng region (SQL)

| Column | Type | Ghi chú |
|--------|------|---------|
| id | BIGINT IDENTITY | PK |
| page_id | BIGINT NOT NULL | FK → page(id) ON DELETE CASCADE |
| region_type | NVARCHAR(50) NOT NULL | CHECK: `BACKGROUND, CHARACTER, TEXT, EFFECT, TONE, OTHER` |
| label | NVARCHAR(255) | Tên vùng (VD: "Castle background") |
| x | INT NOT NULL | Toạ độ X góc trên trái (pixel) |
| y | INT NOT NULL | Toạ độ Y góc trên trái (pixel) |
| width | INT NOT NULL | Chiều rộng vùng (pixel) |
| height | INT NOT NULL | Chiều cao vùng (pixel) |
| color | NVARCHAR(7) | Màu hiển thị vùng trên canvas (hex, VD: `#FF6B6B`) |
| sort_order | INT DEFAULT 0 | Thứ tự render (0 = dưới cùng) |
| status | NVARCHAR(50) DEFAULT 'PENDING' | CHECK: `PENDING, IN_PROGRESS, SUBMITTED, APPROVED, COMPLETED` |
| created_at | DATETIME DEFAULT GETDATE() | |

---

## Danh sách endpoints

| # | Method | Endpoint | Role | Mục đích |
|---|--------|----------|------|----------|
| 1 | GET | `/api/v1/pages/{pageId}/regions` | Authenticated | Danh sách regions của 1 page |
| 2 | POST | `/api/v1/pages/{pageId}/regions` | MANGAKA | Tạo region mới trên page |
| 3 | PUT | `/api/v1/regions/{id}` | MANGAKA | Cập nhật region (label, type, toạ độ, kích thước) |
| 4 | PATCH | `/api/v1/regions/{id}/status` | MANGAKA | Đổi trạng thái region |
| 5 | DELETE | `/api/v1/regions/{id}` | MANGAKA | Xoá region |
| 6 | PUT | `/api/v1/pages/{pageId}/regions/reorder` | MANGAKA | Sắp xếp lại thứ tự regions |

---

## Chi tiết từng endpoint

---

### 1. GET /api/v1/pages/{pageId}/regions

**Mô tả:** Lấy danh sách tất cả regions của 1 page, sắp xếp theo `sort_order` tăng dần. Dùng khi vào workspace để load các vùng vẽ trên page.

**Role:** Authenticated

**Path Variable:**

| Param | Type | Mô tả |
|-------|------|-------|
| `pageId` | Long | ID của page |

**Response 200:**
```json
[
  {
    "id": 500,
    "pageId": 100,
    "regionType": "CHARACTER",
    "label": "Nhân vật chính",
    "x": 500,
    "y": 800,
    "width": 800,
    "height": 1200,
    "color": "#FF6B6B",
    "sortOrder": 1,
    "status": "PENDING",
    "createdAt": "2026-05-30T10:00:00"
  },
  {
    "id": 501,
    "pageId": 100,
    "regionType": "BACKGROUND",
    "label": "Nền lâu đài",
    "x": 0,
    "y": 0,
    "width": 4200,
    "height": 3000,
    "color": "#4ECDC4",
    "sortOrder": 2,
    "status": "APPROVED",
    "createdAt": "2026-05-30T10:00:00"
  }
]
```

**Error responses:**
- `404 Not Found`: Page không tồn tại

---

### 2. POST /api/v1/pages/{pageId}/regions

**Mô tả:** MANGAKA tạo region mới trên page. Khi vẽ vùng trên canvas (kéo thả chuột), frontend gọi API này để lưu region.

**Role:** `MANGAKA`

**Path Variable:**

| Param | Type | Mô tả |
|-------|------|-------|
| `pageId` | Long | ID của page |

**Request Body:**
```json
{
  "regionType": "CHARACTER",
  "label": "Nhân vật chính panel 3",
  "x": 500,
  "y": 800,
  "width": 800,
  "height": 1200,
  "color": "#FF6B6B"
}
```

**Chi tiết các field:**

| Field | Type | Required | Default | Validation |
|-------|------|----------|---------|------------|
| `regionType` | String | ✅ | — | Phải là 1 trong: `BACKGROUND, CHARACTER, TEXT, EFFECT, TONE, OTHER` |
| `label` | String | ❌ | `""` | Tên hiển thị, max 255 ký tự |
| `x` | Integer | ✅ | — | Toạ độ X (>= 0) |
| `y` | Integer | ✅ | — | Toạ độ Y (>= 0) |
| `width` | Integer | ✅ | — | Chiều rộng (> 0) |
| `height` | Integer | ✅ | — | Chiều cao (> 0) |
| `color` | String | ❌ | Tự động sinh theo regionType | Mã màu hex (VD: `#FF6B6B`) |

**Logic:**
```java
public RegionResponse createRegion(Long pageId, RegionRequest request) {
    Page page = pageRepository.findById(pageId)
        .orElseThrow(() -> new ResourceNotFoundException("Page not found: " + pageId));

    // Auto assign sortOrder = max hiện tại + 1
    int maxSortOrder = regionRepository.findByPageIdOrderBySortOrderDesc(pageId)
        .stream().findFirst()
        .map(r -> r.getSortOrder())
        .orElse(0);

    // Auto assign color nếu không gửi
    String color = request.getColor();
    if (color == null || color.isBlank()) {
        color = REGION_DEFAULT_COLORS.getOrDefault(request.getRegionType(), "#6B7280");
    }

    Region region = Region.builder()
        .page(page)
        .regionType(request.getRegionType())
        .label(request.getLabel())
        .x(request.getX())
        .y(request.getY())
        .width(request.getWidth())
        .height(request.getHeight())
        .color(color)
        .sortOrder(maxSortOrder + 1)
        .status(RegionStatus.PENDING)
        .build();

    region = regionRepository.save(region);
    return regionMapper.toResponse(region);
}
```

**Response 201:**
```json
{
  "id": 502,
  "pageId": 100,
  "regionType": "CHARACTER",
  "label": "Nhân vật chính panel 3",
  "x": 500,
  "y": 800,
  "width": 800,
  "height": 1200,
  "color": "#FF6B6B",
  "sortOrder": 3,
  "status": "PENDING",
  "createdAt": "2026-05-30T10:00:00"
}
```

**Error responses:**
- `400 Bad Request`: Region type không hợp lệ, hoặc toạ độ/kích thước âm
- `404 Not Found`: Page không tồn tại

---

### 3. PUT /api/v1/regions/{id}

**Mô tả:** Cập nhật thông tin region. Frontend gọi khi user chỉnh sửa label, type, hoặc kéo thay đổi vị trí/kích thước region trên canvas.

**Role:** `MANGAKA`

**Path Variable:**

| Param | Type | Mô tả |
|-------|------|-------|
| `id` | Long | ID của region cần sửa |

**Request Body:** Tất cả field đều optional (không gửi = giữ nguyên)
```json
{
  "label": "Nhân vật chính (sửa)",
  "regionType": "CHARACTER",
  "x": 600,
  "y": 900,
  "width": 900,
  "height": 1300,
  "color": "#FF4444"
}
```

**Logic:** Chỉ cập nhật các field có trong request (dùng `@BeanMapping(nullValuePropertyMappingStrategy = IGNORE)` với MapStruct, hoặc tự check null bằng tay).

**Response 200:** `RegionResponse` đã cập nhật

**Error responses:**
- `404 Not Found`: Region không tồn tại

---

### 4. PATCH /api/v1/regions/{id}/status

**Mô tả:** Đổi trạng thái region. Khi MANGAKA tạo xong region → mặc định `PENDING`. Khi có task được tạo → chuyển `IN_PROGRESS`. Khi task hoàn thành → `COMPLETED`. Khi duyệt → `APPROVED`.

**Role:** `MANGAKA`

**Path Variable:**

| Param | Type | Mô tả |
|-------|------|-------|
| `id` | Long | ID của region |

**Request Body:**
```json
{
  "status": "IN_PROGRESS"
}
```

**Workflow status:**

| Chuyển từ | → | Thành | Ý nghĩa |
|-----------|---|-------|---------|
| `PENDING` | → | `IN_PROGRESS` | Có task đang được thực hiện |
| `IN_PROGRESS` | → | `SUBMITTED` | Task đã nộp (tự động từ hệ thống task) |
| `SUBMITTED` | → | `APPROVED` | Task đã được duyệt |
| `SUBMITTED` | → | `IN_PROGRESS` | Task yêu cầu sửa lại |
| `IN_PROGRESS` | → | `COMPLETED` | Hoàn thành mà không cần duyệt |

> **Lưu ý:** Khi Task được tạo cho region này, region tự động chuyển `PENDING → IN_PROGRESS`. Khi task được duyệt `APPROVED`, region tự động chuyển `SUBMITTED → APPROVED`. Đây là logic phía Service, không cần frontend gọi riêng.

**Response 200:** `RegionResponse` với status mới

**Error responses:**
- `400 Bad Request`: Chuyển status không hợp lệ
- `404 Not Found`: Region không tồn tại

---

### 5. DELETE /api/v1/regions/{id}

**Mô tả:** Xoá region. Chỉ xoá được khi region chưa có task nào (`PENDING`), hoặc không có task `IN_PROGRESS`.

**Role:** `MANGAKA`

**Path Variable:**

| Param | Type | Mô tả |
|-------|------|-------|
| `id` | Long | ID của region cần xoá |

**Logic trong Service:**
```java
public void deleteRegion(Long id) {
    Region region = regionRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Region not found: " + id));

    // Không cho xoá nếu đang có task IN_PROGRESS
    long activeTasks = taskRepository.countByRegionIdAndStatus(id, TaskStatus.IN_PROGRESS);
    if (activeTasks > 0) {
        throw new BadRequestException("Cannot delete region with active tasks");
    }

    regionRepository.delete(region);
}
```

**Response 204:** No Content

**Error responses:**
- `400 Bad Request`: Region đang có task IN_PROGRESS, không thể xoá
- `404 Not Found`: Region không tồn tại

---

### 6. PUT /api/v1/pages/{pageId}/regions/reorder

**Mô tả:** Sắp xếp lại thứ tự các regions trên page (sau khi kéo thả trong RegionPanel). Frontend gửi mảng region IDs theo thứ tự mới (từ dưới lên trên).

**Role:** `MANGAKA`

**Path Variable:**

| Param | Type | Mô tả |
|-------|------|-------|
| `pageId` | Long | ID của page |

**Request Body:**
```json
{
  "regionIds": [501, 500, 503, 502]
}
```

**Logic:**
```java
public List<RegionResponse> reorderRegions(Long pageId, List<Long> regionIds) {
    List<Region> regions = regionRepository.findByPageIdOrderBySortOrderAsc(pageId);
    
    if (regions.size() != regionIds.size()) {
        throw new BadRequestException("Region count mismatch");
    }

    for (int i = 0; i < regionIds.size(); i++) {
        Long id = regionIds.get(i);
        Region region = regions.stream()
            .filter(r -> r.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Region not found: " + id));
        region.setSortOrder(i);
    }

    regionRepository.saveAll(regions);
    return regions.stream()
        .sorted(Comparator.comparingInt(Region::getSortOrder))
        .map(regionMapper::toResponse)
        .toList();
}
```

**Response 200:** Mảng `RegionResponse[]` sau khi sắp xếp

**Error responses:**
- `400 Bad Request`: Số lượng regionIds không khớp với tổng số regions của page
- `404 Not Found`: Region không tồn tại trong page này

---

## Tổng kết

| # | Method | Endpoint | Request Body | Response | Role |
|---|--------|----------|-------------|----------|------|
| 1 | GET | `/api/v1/pages/{pageId}/regions` | — | `RegionResponse[]` | Authenticated |
| 2 | POST | `/api/v1/pages/{pageId}/regions` | `RegionRequest` | `RegionResponse` (201) | MANGAKA |
| 3 | PUT | `/api/v1/regions/{id}` | `RegionRequest` | `RegionResponse` | MANGAKA |
| 4 | PATCH | `/api/v1/regions/{id}/status` | `{ status }` | `RegionResponse` | MANGAKA |
| 5 | DELETE | `/api/v1/regions/{id}` | — | 204 | MANGAKA |
| 6 | PUT | `/api/v1/pages/{pageId}/regions/reorder` | `{ regionIds[] }` | `RegionResponse[]` | MANGAKA |

## Quan hệ với Task API

```
Region ──has many──> Task
  │                      │
  │ status = PENDING     │ task.status = TODO
  │         ↓            │         ↓
  │ status = IN_PROGRESS │ task.status = IN_PROGRESS (khi có task)
  │         ↓            │         ↓
  │ status = SUBMITTED   │ task → submission nộp bài
  │         ↓            │         ↓
  │ status = APPROVED    │ task → submission APPROVED
  │         ↓            │
  │ status = COMPLETED   │ task.status = DONE
```

Region status tự động cập nhật khi có thay đổi từ Task (trong Service layer), frontend không cần gọi riêng.
