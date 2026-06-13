package com.mangaflow.studio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ── MangaStudioApplication ──
 * Entry point của ứng dụng MangaFlow.
 * <p>
 * 📌 @EnableScheduling:
 * Kích hoạt cơ chế scheduled task của Spring.
 * Cho phép các method đánh dấu @Scheduled (trong AutoPublishService)
 * tự động chạy theo lịch — ở đây là 8h sáng mỗi ngày.
 */
@SpringBootApplication
@EnableScheduling
public class MangaStudioApplication {

    public static void main(String[] args) {
        SpringApplication.run(MangaStudioApplication.class, args);
    }
}
