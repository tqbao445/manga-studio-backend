package com.example.demo.model;

/**
 * ── Role ──
 * Enum định nghĩa các vai trò trong hệ thống MangaFlow.
 *
 * Dùng @Enumerated(EnumType.STRING) trong entity để lưu dạng string
 * xuống DB (thay vì số 0,1,2...), giúp dễ đọc và refactor.
 *
 * Quy ước đặt tên: UPPERCASE, dùng trong code và DB.
 * Khi gắn lên Spring Security: prefix "ROLE_" + tên → "ROLE_MANGAKA"
 */
public enum Role {
    MANGAKA,          // Tác giả chính
    ASSISTANT,        // Trợ lý vẽ
    TANTOU_EDITOR,    // Biên tập viên phụ trách
    EDITORIAL_BOARD   // Ban biên tập (phê duyệt xuất bản)
}
