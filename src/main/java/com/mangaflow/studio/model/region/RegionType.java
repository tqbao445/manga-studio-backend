package com.mangaflow.studio.model.region;

/**
 * ── RegionType ──
 * Enum định nghĩa các loại vùng (region) trên page.
 * <p>
 * Mỗi region là 1 vùng được đánh dấu trên page, tương ứng với
 * 1 thành phần trong bản vẽ mà ASSISTANT cần xử lý.
 * <p>
 * ══════════════════════════════════════════════════════════════
 *  Loại region        │  Ý nghĩa                              │ Màu mặc định
 * ══════════════════════════════════════════════════════════════
 *  BACKGROUND         │  Phông nền (bối cảnh)                 │ #4ECDC4
 *  CHARACTER          │  Nhân vật                             │ #FF6B6B
 *  TEXT               │  Chữ, hội thoại                       │ #FFE66D
 *  EFFECT             │  Hiệu ứng (tia chớp, khói...)         │ #A78BFA
 *  TONE               │  Tone/mảng đen trắng                   │ #6B7280
 *  OTHER              │  Loại khác                            │ #6B7280
 * ══════════════════════════════════════════════════════════════
 * <p>
 * 📌 Mỗi regionType gắn với 1 màu sắc riêng trên canvas
 *    để MANGAKA dễ phân biệt khi vẽ region.
 */
public enum RegionType {
    BACKGROUND,
    CHARACTER,
    TEXT,
    EFFECT,
    TONE,
    OTHER
}
