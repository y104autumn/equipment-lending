package com.example.lending.request;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LendingService {

    private final EquipmentRepository equipmentRepository;
    private final LendingRequestRepository requestRepository;
    private final StatusHistoryRepository historyRepository;

    LendingService(EquipmentRepository equipmentRepository, LendingRequestRepository requestRepository,
            StatusHistoryRepository historyRepository) {
        this.equipmentRepository = equipmentRepository;
        this.requestRepository = requestRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional(readOnly = true)
    public List<EquipmentView> listEquipment() {
        return equipmentRepository.findAllByOrderByIdAsc().stream()
                .map(e -> new EquipmentView(e.getId(), e.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RequestView> list(Status status) {
        return requestRepository.findForList(status).stream().map(RequestView::of).toList();
    }

    @Transactional
    public RequestView create(CreateRequestBody body) {
        Equipment equipment = equipmentRepository.findById(body.equipmentId())
                .orElseThrow(() -> new InvalidInputException("備品 " + body.equipmentId() + " はありません"));
        // WHY: 前後の空白だけの理由は「記入なし」と同じに扱い、NULL で持つ（テーブル定義書: NULL＝記入なし）。
        String reason = body.reason() == null || body.reason().isBlank() ? null : body.reason().strip();
        LendingRequest saved = requestRepository.save(new LendingRequest(
                body.applicantName().strip(), equipment, body.startDate(), body.dueDate(), reason));
        historyRepository.save(new StatusHistory(saved.getId(), null, saved.getStatus()));
        return RequestView.of(saved);
    }

    @Transactional(readOnly = true)
    public AiCheckInput aiCheckInput(Long id) {
        LendingRequest r = requestRepository.findById(id).orElseThrow(() -> new RequestNotFoundException(id));
        return new AiCheckInput(r.getId(), r.getEquipment().getName(), r.getStartDate(), r.getDueDate(),
                r.getReason());
    }

    /** 状態を進め、同じトランザクションで履歴を残す（テーブル定義書の更新責務）。 */
    @Transactional
    public RequestView transition(Long id, Action action) {
        LendingRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new RequestNotFoundException(id));
        Status from = request.apply(action);
        // WHY: 版の照合（楽観ロック）をここで確定させ、競合を 409 として返せるようにする。
        requestRepository.saveAndFlush(request);
        historyRepository.save(new StatusHistory(id, from, request.getStatus()));
        return RequestView.of(request);
    }
}
