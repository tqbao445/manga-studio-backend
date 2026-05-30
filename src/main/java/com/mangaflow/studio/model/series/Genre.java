package com.mangaflow.studio.model.series;

/**
 * ── Genre ──
 * Enum định nghĩa các thể loại truyện tranh.
 *
 * ══════════════════════════════════════════════
 * Giá trị   │  Mô tả           │  Ví dụ
 * ══════════════════════════════════════════════
 * ACTION    │  Hành động       │  Blade of the Demon Moon
 * FANTASY   │  Kỳ ảo           │  Shadow Monarch
 * ROMANCE   │  Lãng mạn        │  Cherry Blossoms After Winter
 * COMEDY    │  Hài hước        │  Phantom Thief Zero
 * DRAMA     │  Chính kịch      │  The Last Samurai
 * ══════════════════════════════════════════════
 *
 * 📌 Dùng để phân loại và filter series trên giao diện.
 * 📌 Frontend SeriesListPage có filter Genre dựa trên enum này.
 */
public enum Genre {
    ACTION,
    FANTASY,
    ROMANCE,
    COMEDY,
    DRAMA
}
