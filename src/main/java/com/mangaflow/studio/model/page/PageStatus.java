package com.mangaflow.studio.model.page;

/**
 * ── PageStatus ──
 * Enum định nghĩa các trạng thái của một page (trang truyện) trong hệ thống.
 *
 * Khi một page được upload lên, nó sẽ đi qua các trạng thái sau:
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  Trạng thái       │  Ý nghĩa                                    │ Flow
 * ═══════════════════════════════════════════════════════════════════════════
 *  UPLOADED         │  Ảnh page đã được upload lên Cloudinary     │
 *                   │  Chưa có region nào được định nghĩa          │ ① BẮT ĐẦU
 * ──────────────────┼──────────────────────────────────────────────┼────────
 *  REGIONS_DEFINED  │  Các region (vùng) trên page đã được vẽ     │
 *                   │  (background, character, text, effect, tone) │ ② Sau khi
 *                   │  Mangaka đã đánh dấu các vùng cần xử lý     │    vẽ region
 * ──────────────────┼──────────────────────────────────────────────┼────────
 *  IN_PRODUCTION    │  Các region đang được giao cho assistant vẽ  │
 *                   │  Có task đang được thực hiện trên page này  │ ③ Khi có
 *                   │                                             │    task active
 * ──────────────────┼──────────────────────────────────────────────┼────────
 *  COMPLETED        │  Tất cả region đã hoàn thành                 │
 *                   │  Page đã sẵn sàng để publish                 │ ④ KẾT THÚC
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 📌 Luồng chính:
 *    UPLOADED → REGIONS_DEFINED → IN_PRODUCTION → COMPLETED
 *
 * 📌 Status được lưu dạng String trong DB (EnumType.STRING)
 *    để dễ đọc và debug.
 *
 * 📌 Khi nào chuyển status?
 *    - UPLOADED → REGIONS_DEFINED : sau khi mangaka vẽ xong regions
 *    - REGIONS_DEFINED → IN_PRODUCTION : khi có task đầu tiên được tạo
 *    - IN_PRODUCTION → COMPLETED : khi tất cả task đã được duyệt
 */
public enum PageStatus {
    UPLOADED,
    REGIONS_DEFINED,
    IN_PRODUCTION,
    COMPLETED
}
