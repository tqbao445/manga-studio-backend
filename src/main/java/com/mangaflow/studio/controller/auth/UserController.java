package com.mangaflow.studio.controller.auth;

import com.mangaflow.studio.dto.auth.mapper.UserMapper;
import com.mangaflow.studio.dto.auth.response.UserDTO;
import com.mangaflow.studio.model.auth.Role;
import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.repository.auth.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ── UserController ──
 * Controller quản lý thông tin người dùng (các endpoint không thuộc auth).
 * <p>
 * 📌 Base URL: /api/users
 * <p>
 * ═══════════════════════════════════════════════════════
 * Endpoint:
 * ═══════════════════════════════════════════════════════
 * GET /api/users/assistants  — Danh sách user có role ASSISTANT
 * ═══════════════════════════════════════════════════════
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Thông tin người dùng — danh sách ASSISTANT để mời")
public class UserController {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    /**
     * Lấy danh sách user có role ASSISTANT trong hệ thống.
     * <p>
     * 📌 Dùng trong:
     * - NewSeriesPage / SeriesDetailPage: dropdown "Chọn assistant để mời"
     * - Bất kỳ UI nào cần hiển thị danh sách assistant
     * <p>
     * 📌 Nếu có ?search=... → filter theo displayName / username (LIKE %search%).
     * Không có → trả về tất cả ASSISTANT (sắp xếp theo tên).
     * <p>
     * 📌 Không cần @PreAuthorize — bất kỳ authenticated user nào cũng có thể
     * xem danh sách assistant (để biết ai có thể mời).
     * <p>
     * 📌 Kết quả trả về là List<UserDTO> — chỉ chứa thông tin cơ bản:
     * id, email, username, displayName, role, avatarUrl
     * KHÔNG chứa password hay thông tin nhạy cảm.
     *
     * @param search từ khoá tìm kiếm (optional) — tìm theo displayName hoặc username
     * @return 200 OK + List<UserDTO> danh sách ASSISTANT
     */
    @Operation(
            summary = "Danh sách user ASSISTANT",
            description = "Lấy danh sách user có role ASSISTANT trong hệ thống. " +
                    "Hỗ trợ tìm kiếm theo tên (?search=...). " +
                    "Dùng trong UI invite assistant để hiển thị dropdown tìm kiếm."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Danh sách ASSISTANT",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserDTO.class))))
    })
    @GetMapping("/assistants")
    public ResponseEntity<List<UserDTO>> getAssistants(
            @Parameter(description = "Từ khoá tìm kiếm (theo displayName hoặc username, không phân biệt hoa thường)", example = "Tran")
            @RequestParam(required = false) String search) {
        List<User> users;

        if (search != null && !search.isBlank()) {
            // Có search → filter theo tên (displayName hoặc username)
            users = userRepository.findByRoleAndSearch(Role.ASSISTANT, search.trim());
        } else {
            // Không có search → trả về tất cả ASSISTANT
            users = userRepository.findByRole(Role.ASSISTANT);
        }

        List<UserDTO> assistants = users.stream()
                .map(userMapper::toDTO) // User → UserDTO (MapStruct)
                .toList();

        return ResponseEntity.ok(assistants);
    }

    /**
     * Lay danh sach user co role TANTOU_EDITOR trong he thong.
     * <p>
     * Dung trong:
     * - SeriesDetailPage: search tantou editor de moi
     * <p>
     * Neu co ?search=... --> filter theo displayName / username (LIKE %search%).
     * Khong co --> tra ve tat ca TANTOU_EDITOR.
     * <p>
     * Giong pattern getAssistants(), chi khac role.
     *
     * @param search tu khoa tim kiem (optional)
     * @return 200 OK + List&lt;UserDTO&gt; danh sach TANTOU_EDITOR
     */
    @GetMapping("/tantou-editors")
    public ResponseEntity<List<UserDTO>> getTantouEditors(
            @RequestParam(required = false) String search) {
        List<User> users;

        if (search != null && !search.isBlank()) {
            users = userRepository.findByRoleAndSearch(Role.TANTOU_EDITOR, search.trim());
        } else {
            users = userRepository.findByRole(Role.TANTOU_EDITOR);
        }

        List<UserDTO> dtos = users.stream()
                .map(userMapper::toDTO)
                .toList();

        return ResponseEntity.ok(dtos);
    }

    /**
     * Lấy danh sách user có role EDITORIAL_BOARD trong hệ thống.
     * Dùng trong CreateMeetingModal để chọn participant cho cuộc họp.
     *
     * Chỉ CHIEF_EDITOR mới có thể gọi endpoint này.
     *
     * @param search từ khoá tìm kiếm (optional)
     * @return 200 OK + List<UserDTO> danh sách EDITORIAL_BOARD
     */
    @GetMapping("/board-members")
    @PreAuthorize("hasRole('CHIEF_EDITOR')")
    public ResponseEntity<List<UserDTO>> getBoardMembers(
            @RequestParam(required = false) String search) {
        List<User> users;

        if (search != null && !search.isBlank()) {
            users = userRepository.findByRoleAndSearch(Role.EDITORIAL_BOARD, search.trim());
        } else {
            users = userRepository.findByRole(Role.EDITORIAL_BOARD);
        }

        List<UserDTO> dtos = users.stream()
                .map(userMapper::toDTO)
                .toList();

        return ResponseEntity.ok(dtos);
    }
}
