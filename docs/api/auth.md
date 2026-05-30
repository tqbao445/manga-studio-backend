# Auth API — `/api/auth`

Base URL: `http://localhost:8080/api/auth`

> 🔐 **Luồng xác thực**
> 1. Frontend gọi `POST /api/auth/register` hoặc `POST /api/auth/login`
> 2. Backend trả về `accessToken` (JWT) + `user` info
> 3. Frontend lưu token (localStorage/sessionStorage)
> 4. Gắn token vào Header: `Authorization: Bearer <token>`
> 5. Token hết hạn sau **24 giờ** (`86400000ms`)
> 6. Các API khác trả về `401` nếu token thiếu/hết hạn/sai

> 📌 **Test account (seed data)**
> - MANGAKA: `ichikawa@manga.com` / `password`
> - MANGAKA: `fujimoto@manga.com` / `password`
> - MANGAKA: `ito@manga.com` / `password`
> - TANTOU_EDITOR: `sato@editor.com` / `password`
> - EDITORIAL_BOARD: `kimura@board.com` / `password`

---

## 1. REGISTER — Đăng ký tài khoản

```
POST /api/auth/register
```

**Request Body (JSON):**
```json
{
  "email": "user@example.com",
  "username": "username123",
  "password": "password123",
  "displayName": "User 123",
  "role": "MANGAKA"
}
```

| Field       | Type   | Required | Mô tả                                     |
|-------------|--------|----------|-------------------------------------------|
| email       | string | ✅       | Email, phải unique                        |
| username    | string | ✅       | Username, 3-50 ký tự, phải unique         |
| password    | string | ✅       | Mật khẩu, tối thiểu 6 ký tự               |
| displayName | string | ❌       | Tên hiển thị (mặc định = username nếu null)|
| role        | string | ❌       | Mặc định `MANGAKA` nếu null               |

**Các role hợp lệ:** `MANGAKA`, `ASSISTANT`, `TANTOU_EDITOR`, `EDITORIAL_BOARD`

**Response 201 (Created):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "user": {
    "id": 11,
    "email": "user@example.com",
    "username": "username123",
    "displayName": "User 123",
    "role": "MANGAKA",
    "avatarUrl": null,
    "bio": null
  }
}
```

| Error | Status | Mô tả |
|-------|--------|-------|
| Email already registered | 409 | Email đã tồn tại |
| Username already taken | 409 | Username đã có người dùng |

---

## 2. LOGIN — Đăng nhập

```
POST /api/auth/login
```

**Request Body (JSON):**
```json
{
  "email": "ichikawa@manga.com",
  "password": "password"
}
```

| Field    | Type   | Required | Mô tả |
|----------|--------|----------|-------|
| email    | string | ✅       | Email đã đăng ký |
| password | string | ✅       | Mật khẩu |

**Response 200 (OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "user": {
    "id": 1,
    "email": "ichikawa@manga.com",
    "username": "ichikawa",
    "displayName": "Ichikawa",
    "role": "MANGAKA",
    "avatarUrl": null,
    "bio": null
  }
}
```

| Error | Status | Mô tả |
|-------|--------|-------|
| Invalid credentials | 401 | Sai email hoặc mật khẩu |

---

## 3. GET ME — Lấy thông tin user hiện tại

```
GET /api/auth/me
Authorization: Bearer <token>
```

> 🔒 Yêu cầu: **isAuthenticated()** — tất cả user đã đăng nhập

**Response 200:**
```json
{
  "id": 1,
  "email": "ichikawa@manga.com",
  "username": "ichikawa",
  "displayName": "Ichikawa",
  "role": "MANGAKA",
  "avatarUrl": null,
  "bio": null
}
```

---

## 4. UPDATE PROFILE — Cập nhật thông tin

```
PATCH /api/auth/profile
Authorization: Bearer <token>
```

> 🔒 Yêu cầu: **isAuthenticated()** — tất cả user đã đăng nhập

**Request Body (JSON):** (chỉ gửi field muốn đổi, null = giữ nguyên)
```json
{
  "displayName": "New Name",
  "avatarUrl": "https://example.com/avatar.jpg",
  "bio": "Một mangaka đam mê sáng tác"
}
```

| Field       | Type   | Required | Mô tả |
|-------------|--------|----------|-------|
| displayName | string | ❌       | Tên hiển thị mới |
| avatarUrl   | string | ❌       | URL ảnh đại diện |
| bio         | string | ❌       | Giới thiệu bản thân |

**Response 200:**
```json
{
  "id": 1,
  "email": "ichikawa@manga.com",
  "username": "ichikawa",
  "displayName": "New Name",
  "role": "MANGAKA",
  "avatarUrl": "https://example.com/avatar.jpg",
  "bio": "Một mangaka đam mê sáng tác"
}
```

---

## 📦 Response Models

### AuthResponse
```json
{
  "accessToken": "string (JWT token)",
  "user": { "UserDTO" }
}
```

### UserDTO
```json
{
  "id": "number",
  "email": "string",
  "username": "string",
  "displayName": "string",
  "role": "string (MANGAKA | ASSISTANT | TANTOU_EDITOR | EDITORIAL_BOARD)",
  "avatarUrl": "string | null",
  "bio": "string | null"
}
```
