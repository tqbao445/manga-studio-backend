package com.mangaflow.studio.service.series;

import com.mangaflow.studio.common.exception.AppException;
import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.series.mapper.SeriesMapper;
import com.mangaflow.studio.dto.series.request.ApproveRequest;
import com.mangaflow.studio.dto.series.request.RejectRequest;

import com.mangaflow.studio.dto.series.request.UpdateStatusRequest;
import com.mangaflow.studio.dto.series.response.SeriesResponse;
import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.model.series.Series;
import com.mangaflow.studio.model.series.SeriesStatus;
import com.mangaflow.studio.repository.auth.UserRepository;
import com.mangaflow.studio.repository.series.SeriesRepository;
import com.mangaflow.studio.service.common.WebSocketService;
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
 *                          ┌──────────┐
 *                    ┌──── │  DRAFT   │ ◄────── tantouReject()
 *                    │     └────┬─────┘
 *                    │          │ submitToTantou()
 *                    │          ▼
 *                    │   ┌───────────────┐
 *                    │   │ PENDING_TANTOU│
 *                    │   └──┬────────┬───┘
 *                    │      │ tantou │ tantou
 *                    │      │Approve │ Reject
 *                    │      ▼        ▼
 *                    │ ┌─────────────────┐
 *                    │ │PENDING_BOARD_VOTE│
 *                    │ └──┬──────────┬───┘
 *                    │    │ EB       │ EB
 *                    │    │finalize  │ finalize
 *                    │    ▼          ▼
 *                    │ ┌────────┐ ┌───────┐
 *                    │ │ONGOING │ │ DRAFT │
 *                    │ └───┬────┘ └───────┘
 *                    │     │ updateStatus()
 *                    │     ├────► HIATUS
 *                    │     ├────► AT_RISK
 *                    │     ├────► CANCELLED
 *                    │     └────► COMPLETED
 *                    │
 *              (Legacy path: DRAFT → PENDING_APPROVAL → ONGOING/DRAFT)
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

    /**
     * WebSocketService: Push real-time notifications khi có sự kiện
     * submit, approve, reject...
     */
    private final WebSocketService webSocketService;

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
        Series series = seriesRepository.findByIdAndMangakaId(id, user.getUserId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Series not found or not owned by you"));

        if (series.getStatus() != SeriesStatus.DRAFT) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Only DRAFT series can be submitted");
        }

        series.setStatus(SeriesStatus.PENDING_APPROVAL);

        return seriesMapper.toResponse(seriesRepository.save(series));
    }

    // ════════════════════════════════════════════════════════════
    // 1b. SUBMIT TO TANTOU — Gửi cho Tantou Editor (Mangaka)
    // ════════════════════════════════════════════════════════════

    /**
     * Gửi series cho Tantou Editor phê duyệt.
     * DRAFT → PENDING_TANTOU
     *
     * Điều kiện:
     * - Series phải DRAFT.
     * - Series phải có tantouEditor != null.
     *
     * @param id   ID series
     * @param user Mangaka đang đăng nhập
     * @return SeriesResponse với status = PENDING_TANTOU
     */
    @Transactional
    public SeriesResponse submitToTantou(Long id, CustomUserDetails user) {
        Series series = seriesRepository.findByIdAndMangakaId(id, user.getUserId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Series not found or not owned by you"));

        if (series.getStatus() != SeriesStatus.DRAFT) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Only DRAFT series can be submitted to tantou");
        }

        if (series.getTantouEditor() == null) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Series must have a tantou editor assigned before submission");
        }

        series.setStatus(SeriesStatus.PENDING_TANTOU);
        SeriesResponse response = seriesMapper.toResponse(seriesRepository.save(series));

        webSocketService.sendToUser(series.getTantouEditor().getId(),
                "TANTOU_REVIEW_REQUIRED",
                "Series \"" + series.getTitle() + "\" has been submitted for your review");

        return response;
    }

    // ════════════════════════════════════════════════════════════
    // 2b. TANTOU APPROVE — Tantou đồng ý (TANTOU_EDITOR)
    // ════════════════════════════════════════════════════════════

    /**
     * Tantou Editor phê duyệt series.
     * PENDING_TANTOU → PENDING_BOARD_VOTE
     *
     * Điều kiện:
     * - Series phải PENDING_TANTOU.
     * - User gọi phải là tantouEditor của series.
     *
     * @param id   ID series
     * @param user Tantou đang đăng nhập
     * @return SeriesResponse với status = PENDING_BOARD_VOTE
     */
    @Transactional
    public SeriesResponse tantouApprove(Long id, CustomUserDetails user) {
        Series series = seriesRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Series not found"));

        if (series.getStatus() != SeriesStatus.PENDING_TANTOU) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Series is not pending tantou review");
        }

        if (series.getTantouEditor() == null || !series.getTantouEditor().getId().equals(user.getUserId())) {
            throw new AppException(HttpStatus.FORBIDDEN,
                    "You are not the assigned tantou editor for this series");
        }

        series.setStatus(SeriesStatus.PENDING_BOARD_VOTE);
        SeriesResponse response = seriesMapper.toResponse(seriesRepository.save(series));

        webSocketService.sendToUser(series.getMangaka().getId(),
                "TANTOU_APPROVED",
                "Series \"" + series.getTitle() + "\" has been approved by your tantou editor");

        return response;
    }

    // ════════════════════════════════════════════════════════════
    // 2c. TANTOU REJECT — Tantou từ chối (TANTOU_EDITOR)
    // ════════════════════════════════════════════════════════════

    /**
     * Tantou Editor từ chối series, đưa về DRAFT.
     * PENDING_TANTOU → DRAFT
     *
     * Điều kiện:
     * - Series phải PENDING_TANTOU.
     * - User gọi phải là tantouEditor của series.
     *
     * @param id      ID series
     * @param request chứa reason (optional)
     * @param user    Tantou đang đăng nhập
     * @return SeriesResponse với status = DRAFT
     */
    @Transactional
    public SeriesResponse tantouReject(Long id, CustomUserDetails user) {
        Series series = seriesRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Series not found"));

        if (series.getStatus() != SeriesStatus.PENDING_TANTOU) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Series is not pending tantou review");
        }

        if (series.getTantouEditor() == null || !series.getTantouEditor().getId().equals(user.getUserId())) {
            throw new AppException(HttpStatus.FORBIDDEN,
                    "You are not the assigned tantou editor for this series");
        }

        series.setStatus(SeriesStatus.DRAFT);
        SeriesResponse response = seriesMapper.toResponse(seriesRepository.save(series));

        String message = "Series \"" + series.getTitle() + "\" has been rejected by Lead Editor.";
        webSocketService.sendToUser(series.getMangaka().getId(),
                "TANTOU_REJECTED",
                message);

        return response;
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
 *    - DRAFT: có endpoint submitToTantou
 *    - PENDING_APPROVAL: legacy — dùng submitForApproval / approve / reject
 *    - PENDING_TANTOU: dùng tantouApprove / tantouReject
 *    - PENDING_BOARD_VOTE: dùng EB vote module (BE2)
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
