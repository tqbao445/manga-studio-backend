package com.mangaflow.studio.series.service;

import com.mangaflow.studio.auth.model.User;
import com.mangaflow.studio.auth.repository.UserRepository;
import com.mangaflow.studio.common.exception.AppException;
import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.series.dto.mapper.SeriesMapper;
import com.mangaflow.studio.series.dto.request.ApproveRequest;
import com.mangaflow.studio.series.dto.request.RejectRequest;
import com.mangaflow.studio.series.dto.request.UpdateStatusRequest;
import com.mangaflow.studio.series.dto.response.SeriesResponse;
import com.mangaflow.studio.series.enums.SeriesStatus;
import com.mangaflow.studio.series.model.Series;
import com.mangaflow.studio.series.repository.SeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ── SeriesWorkflowService ──
 * Service chịu trách nhiệm quy trình duyệt (approval workflow)
 * và chuyển trạng thái (state machine) của Series.
 *
 * 📌 Tách biệt khỏi SeriesService vì:
 *    - Workflow có business logic phức tạp (state transitions, editorial gán editor).
 *    - Workflow chỉ liên quan đến status, không liên quan đến CRUD thông tin series.
 *    - Dễ bảo trì — thay đổi quy trình duyệt không ảnh hưởng CRUD.
 *
 * ═══════════════════════════════════════════════════════
 *  State Machine (biểu đồ trạng thái):
 * ═══════════════════════════════════════════════════════
 *
 *         ┌──────────┐
 *         │  DRAFT   │ ◄────────────── reject()
 *         └────┬─────┘
 *              │ submitForApproval()
 *              ▼
 *    ┌─────────────────┐
 *    │PENDING_APPROVAL │
 *    └──┬──────────┬───┘
 *       │ approve()│ reject()
 *       ▼          ▼
 *   ┌────────┐ ┌───────┐
 *   │ONGOING │ │ DRAFT │ (quay lại)
 *   └───┬────┘ └───────┘
 *       │ updateStatus()
 *       ├────► HIATUS
 *       ├────► AT_RISK
 *       ├────► CANCELLED
 *       └────► COMPLETED
 *
 * 📌 Các trạng thái không có transition:
 *    APPROVED, REJECTED — dự phòng cho tương lai, chưa dùng.
 */
@Service
@RequiredArgsConstructor
public class SeriesWorkflowService {

    // ────────────────────────────────────────────────────────────
    // Repository & Dependencies
    // ────────────────────────────────────────────────────────────

    /**
     * seriesRepository: Repository chính — thao tác với bảng series.
     * Dùng để tìm series cần thay đổi trạng thái.
     */
    private final SeriesRepository seriesRepository;

    /**
     * userRepository: Dùng để tìm User entity khi gán tantou editor
     * trong quá trình approve series.
     */
    private final UserRepository userRepository;

    /**
     * seriesMapper: MapStruct — chuyển đổi Series → SeriesResponse.
     * Dùng ở mọi method để trả về response sau khi thay đổi status.
     */
    private final SeriesMapper seriesMapper;

    // ════════════════════════════════════════════════════════════
    // 1. SUBMIT — Gửi duyệt (Mangaka)
    // ════════════════════════════════════════════════════════════

    /**
     * Gửi series cho Editorial Board phê duyệt.
     *
     * 📌 State transition: DRAFT → PENDING_APPROVAL
     *
     * 📌 Ai gọi:
     *    Chỉ MANGAKA (chủ sở hữu) — @PreAuthorize ở Controller.
     *    Kiểm tra ownership bằng findByIdAndMangakaId().
     *
     * 📌 Điều kiện:
     *    Series phải đang ở trạng thái DRAFT.
     *    Nếu không → throw BAD_REQUEST.
     *
     * 📌 Sau khi submit:
     *    Editorial Board sẽ xem xét và approve hoặc reject.
     *    Mangaka không thể tự approve — tránh xung đột lợi ích.
     *
     * @param id   ID của series cần gửi duyệt
     * @param user user đang đăng nhập (MANGAKA)
     * @return SeriesResponse với status = PENDING_APPROVAL
     * @throws AppException 404 — nếu không tìm thấy hoặc không phải chủ
     * @throws AppException 400 — nếu series không ở trạng thái DRAFT
     */
    @Transactional
    public SeriesResponse submitForApproval(Long id, CustomUserDetails user) {
        // Kiểm tra ownership: series này có thuộc về user không?
        Series series = seriesRepository.findByIdAndMangakaId(id, user.getUserId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Series not found or not owned by you"));

        // Chỉ DRAFT mới có thể submit lên PENDING_APPROVAL
        if (series.getStatus() != SeriesStatus.DRAFT) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Only DRAFT series can be submitted");
        }

        // Chuyển trạng thái
        series.setStatus(SeriesStatus.PENDING_APPROVAL);

        // Lưu + MapStruct Series → SeriesResponse
        return seriesMapper.toResponse(seriesRepository.save(series));
    }

    // ════════════════════════════════════════════════════════════
    // 2. APPROVE — Phê duyệt (Editorial Board)
    // ════════════════════════════════════════════════════════════

    /**
     * Phê duyệt series — đưa lên trạng thái ONGOING.
     *
     * 📌 State transition: PENDING_APPROVAL → ONGOING
     *
     * 📌 Ai gọi:
     *    Chỉ EDITORIAL_BOARD — @PreAuthorize ở Controller.
     *    Không cần ownership check (Editorial Board quản lý tất cả series).
     *
     * 📌 Điều kiện:
     *    Series phải đang PENDING_APPROVAL.
     *    Nếu không → throw BAD_REQUEST.
     *
     * 📌 Gán Tantou Editor (tuỳ chọn):
     *    Khi approve, Editorial Board có thể gán một biên tập viên
     *    phụ trách series này (tantouEditor).
     *    Nếu request.tantouEditorId != null → tìm User và gán.
     *    Editor phải có role TANTOU_EDITOR (hoặc bất kỳ, tuỳ logic sau này).
     *    Nếu editor không tồn tại → throw 404.
     *
     * @param id      ID của series cần duyệt
     * @param request chứa tantouEditorId (tuỳ chọn) — gán editor phụ trách
     * @param user    user đang đăng nhập (EDITORIAL_BOARD)
     * @return SeriesResponse với status = ONGOING
     * @throws AppException 404 — nếu không tìm thấy series hoặc editor
     * @throws AppException 400 — nếu series không ở trạng thái PENDING_APPROVAL
     */
    @Transactional
    public SeriesResponse approve(Long id, ApproveRequest request, CustomUserDetails user) {
        Series series = seriesRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Series not found"));

        if (series.getStatus() != SeriesStatus.PENDING_APPROVAL) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Series is not pending approval");
        }

        series.setStatus(SeriesStatus.ONGOING);

        if (request.getTantouEditorId() != null) {
            User editor = userRepository.findById(request.getTantouEditorId())
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Editor not found"));
            series.setTantouEditor(editor);
        }

        return seriesMapper.toResponse(seriesRepository.save(series));
    }

    // ════════════════════════════════════════════════════════════
    // 3. REJECT — Từ chối (Editorial Board)
    // ════════════════════════════════════════════════════════════

    /**
     * Từ chối series — đưa về trạng thái DRAFT để mangaka sửa lại.
     *
     * 📌 State transition: PENDING_APPROVAL → DRAFT
     *
     * 📌 Ai gọi:
     *    Chỉ EDITORIAL_BOARD — @PreAuthorize ở Controller.
     *
     * 📌 Điều kiện:
     *    Series phải đang PENDING_APPROVAL.
     *
     * 📌 Sau khi reject:
     *    Mangaka có thể sửa lại thông tin series và submit lại.
     *    Editorial Board có thể ghi lý do từ chối trong request.notes.
     *
     * @param id      ID của series cần từ chối
     * @param request (hiện tại chỉ dùng notes để log, không ảnh hưởng state)
     * @param user    user đang đăng nhập (EDITORIAL_BOARD)
     * @return SeriesResponse với status = DRAFT
     * @throws AppException 404 — nếu không tìm thấy series
     * @throws AppException 400 — nếu series không ở trạng thái PENDING_APPROVAL
     */
    @Transactional
    public SeriesResponse reject(Long id, RejectRequest request, CustomUserDetails user) {
        Series series = seriesRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Series not found"));

        if (series.getStatus() != SeriesStatus.PENDING_APPROVAL) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Series is not pending approval");
        }

        series.setStatus(SeriesStatus.DRAFT);

        return seriesMapper.toResponse(seriesRepository.save(series));
    }

    // ════════════════════════════════════════════════════════════
    // 4. UPDATE STATUS — Chuyển trạng thái (Editorial Board)
    // ════════════════════════════════════════════════════════════

    /**
     * Thay đổi trạng thái series (dành cho Editorial Board).
     *
     * 📌 Ai gọi:
     *    Chỉ EDITORIAL_BOARD — @PreAuthorize ở Controller.
     *
     * 📌 Khác với approve/reject:
     *    approve/reject chỉ xử lý PENDING_APPROVAL → ONGOING/DRAFT.
     *    updateStatus xử lý các chuyển đổi giữa ONGOING, HIATUS, AT_RISK,...
     *
     * 📌 State machine validation:
     *    Gọi isValidTransition() để kiểm tra transition hợp lệ.
     *    Nếu không hợp lệ → throw BAD_REQUEST.
     *
     * 📌 Các transition được phép:
     *    ┌──────────┬──────────────────────────────────────┐
     *    │ Từ       │ Đến                                   │
     *    ├──────────┼──────────────────────────────────────┤
     *    │ ONGOING  │ HIATUS, AT_RISK, CANCELLED, COMPLETED │
     *    │ HIATUS   │ ONGOING, CANCELLED                    │
     *    │ AT_RISK  │ ONGOING, CANCELLED                    │
     *    └──────────┴──────────────────────────────────────┘
     *
     * @param id      ID của series
     * @param request chứa status muốn chuyển đến (@NotNull)
     * @param user    user đang đăng nhập (EDITORIAL_BOARD)
     * @return SeriesResponse với status mới
     * @throws AppException 404 — nếu không tìm thấy series
     * @throws AppException 400 — nếu transition không hợp lệ
     */
    @Transactional
    public SeriesResponse updateStatus(Long id, UpdateStatusRequest request, CustomUserDetails user) {
        Series series = seriesRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Series not found"));

        SeriesStatus current = series.getStatus();
        SeriesStatus target = request.getStatus();

        if (!isValidTransition(current, target)) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Cannot transition from " + current + " to " + target);
        }

        series.setStatus(target);

        return seriesMapper.toResponse(seriesRepository.save(series));
    }

    // ════════════════════════════════════════════════════════════
    // PRIVATE HELPER: State Machine Validator
    // ════════════════════════════════════════════════════════════

    /**
     * Kiểm tra xem việc chuyển trạng thái từ current → target có hợp lệ không.
     *
     * 📌 Dùng switch expression (Java 21):
     *    Gọn và rõ ràng hơn if-else chain.
     *    Mỗi case trả về boolean trực tiếp.
     *
     * 📌 State machine rules:
     *
     *    ┌──────────┬──────────────┬──────────────────────────────┐
     *    │ Current  │ Target(s)    │ Ý nghĩa                      │
     *    ├──────────┼──────────────┼──────────────────────────────┤
     *    │ ONGOING  │ HIATUS       │ Tạm ngưng vì lý do sức khoẻ  │
     *    │ ONGOING  │ AT_RISK      │ Cảnh báo — nguy cơ bị huỷ    │
     *    │ ONGOING  │ CANCELLED    │ Huỷ bỏ — không tiếp tục      │
     *    │ ONGOING  │ COMPLETED    │ Kết thúc — hoàn thành        │
     *    ├──────────┼──────────────┼──────────────────────────────┤
     *    │ HIATUS   │ ONGOING      │ Quay lại — tiếp tục sản xuất │
     *    │ HIATUS   │ CANCELLED    │ Huỷ hẳn                      │
     *    ├──────────┼──────────────┼──────────────────────────────┤
     *    │ AT_RISK  │ ONGOING      │ Thoát cảnh báo — ổn định lại │
     *    │ AT_RISK  │ CANCELLED    │ Huỷ                          │
     *    └──────────┴──────────────┴──────────────────────────────┘
     *
     * 📌 Các trạng thái không được phép:
     *    - DRAFT, PENDING_APPROVAL: có endpoint riêng (submit, approve, reject)
     *    - CANCELLED, COMPLETED: không thể chuyển tiếp (terminal states)
     *    - APPROVED, REJECTED: chưa dùng đến
     *
     * @param current trạng thái hiện tại của series
     * @param target  trạng thái muốn chuyển đến
     * @return true nếu transition hợp lệ, false nếu không
     */
    private boolean isValidTransition(SeriesStatus current, SeriesStatus target) {
        return switch (current) {
            // ONGOING → HIATUS (tạm ngưng)
            //         → AT_RISK (cảnh báo)
            //         → CANCELLED (huỷ)
            //         → COMPLETED (kết thúc)
            case ONGOING -> target == SeriesStatus.HIATUS
                    || target == SeriesStatus.AT_RISK
                    || target == SeriesStatus.CANCELLED
                    || target == SeriesStatus.COMPLETED;

            // HIATUS → ONGOING (quay lại sản xuất)
            //        → CANCELLED (huỷ hẳn)
            case HIATUS -> target == SeriesStatus.ONGOING
                    || target == SeriesStatus.CANCELLED;

            // AT_RISK → ONGOING (thoát cảnh báo)
            //         → CANCELLED (huỷ)
            case AT_RISK -> target == SeriesStatus.ONGOING
                    || target == SeriesStatus.CANCELLED;

            // Các status khác (DRAFT, PENDING_APPROVAL, CANCELLED, COMPLETED...)
            // không hỗ trợ transition qua endpoint này
            default -> false;
        };
    }
}
