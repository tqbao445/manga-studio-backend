# Docker Setup — Manga Studio Backend

## Yêu cầu

- [Docker Desktop](https://www.docker.com/products/docker-desktop/)
- ~5GB ổ cứng trống

---

## 1. Lần đầu chạy

```bash
# Clone repo
git clone https://github.com/tqbao445/manga-studio-backend.git
cd manga-studio-backend

# Tạo file .env từ mẫu
cp .env.example .env

# Build & khởi động
docker compose up -d
```

⏱ Lần đầu mất **5-10 phút** (download image SQL Server + build Java).

### Kiểm tra

```bash
# Xem trạng thái containers
docker compose ps

# Xem log chờ backend start xong
docker compose logs --tail=10 backend
# Tìm dòng "Started MangaStudioApplication"
```

### Truy cập

| URL | Chức năng |
|-----|-----------|
| `http://localhost:8080/swagger-ui.html` | Swagger UI |
| `http://localhost:8080/v3/api-docs` | OpenAPI JSON |
| `localhost:1433` | SQL Server |

> SQL Server user: `sa`, password: trong file `.env`

---

## 2. Các lệnh hằng ngày

```bash
# Khởi động lại
docker compose up -d

# Xem log
docker compose logs -f                # tất cả services
docker compose logs -f backend        # chỉ backend
docker compose logs --tail=50 backend # 50 dòng cuối

# Rebuild khi code thay đổi
docker compose up -d --build

# Dừng (giữ data)
docker compose down

# Dừng + xoá data (reset DB)
docker compose down -v
```

---

## 3. Cấu trúc

```
manga-studio-backend/
├── Dockerfile              # Build backend (multi-stage)
├── docker-compose.yml      # 3 services
├── .env                    # Biến môi trường (git ignored)
└── .env.example            # Mẫu .env
```

| Service | Container | Port | Chức năng |
|---------|-----------|------|-----------|
| `sqlserver` | `manga-studio-db` | 1433 | SQL Server |
| `db-setup` | `manga-studio-db-setup` | — | Tạo DB rồi thoát |
| `backend` | `manga-studio-backend` | 8080 | Spring Boot API |

---

## 4. Lưu ý

- **Apple Silicon (M1/M2/M3/M4):** SQL Server chạy qua giả lập AMD64, khởi động chậm hơn (~1-2 phút).
- **Đổi mật khẩu:** Sửa `MSSQL_SA_PASSWORD` trong `.env` → `docker compose down -v` → `docker compose up -d`.
- **Port conflict:** Nếu port 1433 hoặc 8080 đã được dùng, sửa trong `docker-compose.yml` ở phần `ports:`.
