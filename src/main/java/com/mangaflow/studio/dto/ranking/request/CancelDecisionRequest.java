package com.mangaflow.studio.dto.ranking.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 📤 CancelDecisionRequest - DTO cho yêu cầu Chief Editor quyết định giữ/hủy series.
 * <p>
 * Khi series ở tier D quá 3 tháng liên tiếp, Chief Editor có thể:
 * - KEEP: giữ lại → reset warning, về ONGOING
 * - CANCEL: hủy → chuyển CANCELLED
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancelDecisionRequest {
    private String decision;  // "KEEP" hoặc "CANCEL"
}
