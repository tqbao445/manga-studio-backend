package com.mangaflow.studio.model.series;

/**
 * ── TargetDemographic ──
 * Enum định nghĩa đối tượng độc giả mục tiêu của series.
 *
 * ══════════════════════════════════════════════════════════
 * Giá trị   │  Nhóm tuổi         │  Đặc điểm
 * ══════════════════════════════════════════════════════════
 * SHONEN    │  Nam thiếu niên    │  Hành động, phiêu lưu, shounen
 * SHOJO     │  Nữ thiếu niên     │  Lãng mạn, tình cảm
 * SEINEN    │  Nam trưởng thành  │  Kịch tính, tâm lý, mature
 * JOSEI     │  Nữ trưởng thành   │  Đời thường, tình cảm người lớn
 * ══════════════════════════════════════════════════════════
 *
 * 📌 Đây là phân loại đặc thù của manga Nhật Bản.
 * 📌 Dùng để filter series và đề xuất nội dung phù hợp.
 */
public enum TargetDemographic {
    SHONEN,
    SHOJO,
    SEINEN,
    JOSEI
}
