# MangaStudio Backend

Spring Boot 3.2.5 + SQL Server + JWT Authentication

## Tech Stack
- Java 21, Spring Boot 3.2.5, Spring Security, Spring Data JPA
- SQL Server, JWT (jjwt 0.12.5), Lombok, Springdoc OpenAPI

## Quick Start
```bash
mvn spring-boot:run
```

Swagger UI: http://localhost:8080/swagger-ui.html

## Database
- SQL Server at `localhost:1433`, database `manga_studio`
- `ddl-auto=update` — Hibernate tự động tạo/cập nhật bảng

## Seed Data
10 demo users are auto-seeded on first run (password: `password`).
