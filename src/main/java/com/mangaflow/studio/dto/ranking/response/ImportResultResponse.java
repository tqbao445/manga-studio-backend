package com.mangaflow.studio.dto.ranking.response;

import lombok.*;
import java.util.List;

/**
 * 📤 ImportResultResponse - DTO trả về kết quả import file Excel.
 * <p>
 * Sau khi Editorial Board upload file Excel metrics, hệ thống trả về:
 * - Tổng số dòng đã xử lý
 * - Số dòng thành công
 * - Danh sách các dòng bị lỗi (kèm số dòng và lý do)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportResultResponse {
    private int totalRows;                  // Tổng số dòng dữ liệu trong file (không tính header)
    private int successRows;               // Số dòng import thành công
    private List<ErrorRow> errorRows;      // Danh sách các dòng bị lỗi

    /**
     * Lớp con đại diện cho 1 dòng bị lỗi khi import.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorRow {
        private int rowNumber;              // Số dòng trong file Excel (1-indexed, tính cả header)
        private String message;            // Lý do lỗi (VD: "Series not found with id: 999")
    }
}
