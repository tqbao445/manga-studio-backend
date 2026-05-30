# MangaFlow API Documentation

Base URL: `http://localhost:8080`

> 📘 **Swagger UI:** http://localhost:8080/swagger-ui.html
> 📗 **OpenAPI JSON:** http://localhost:8080/v3/api-docs

---

## Modules

| Module | Base Path | File | Mô tả |
|--------|-----------|------|-------|
| Auth | `/api/auth` | [auth.md](./auth.md) | Đăng ký, đăng nhập, profile |
| Series | `/api/series` | [series.md](./series.md) | CRUD series, workflow duyệt |
| Page | `/api/v1` | [pages.md](./pages.md) | Upload, batch, reorder pages |

---

## Authentication

Tất cả API (trừ register/login) yêu cầu **Bearer token** trong Header:

```
Authorization: Bearer <access_token>
```

Token được lấy từ response của `POST /api/auth/login` hoặc `POST /api/auth/register`.

---

## Error Response Format

Tất cả lỗi trả về dạng:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Mô tả lỗi chi tiết",
  "timestamp": "2026-05-29T22:00:00"
}
```

---

## Test Accounts (seed data)

| Email | Password | Role |
|-------|----------|------|
| ichikawa@manga.com | password | MANGAKA |
| fujimoto@manga.com | password | MANGAKA |
| ito@manga.com | password | MANGAKA |
| tanaka@manga.com | password | ASSISTANT |
| sato@editor.com | password | TANTOU_EDITOR |
| kimura@board.com | password | EDITORIAL_BOARD |

---

## Config

| Config | Giá trị | Mô tả |
|--------|---------|-------|
| Server port | 8080 | Cổng backend |
| JWT expiration | 24h | Token hết hạn sau 24 giờ |
| Max file upload | 50MB | Mỗi file tối đa |
| Max request upload | 100MB | Tổng request tối đa (batch) |
