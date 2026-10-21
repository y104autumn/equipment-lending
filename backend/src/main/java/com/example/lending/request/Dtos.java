package com.example.lending.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** 申請の入力（要件定義書 §5）。 */
record CreateRequestBody(
        @NotBlank(message = "申請者名は必須です") @Size(max = 50, message = "申請者名は 50 字までです") String applicantName,
        @NotNull(message = "備品は必須です") Long equipmentId,
        @NotNull(message = "開始日は必須です") LocalDate startDate,
        @NotNull(message = "返却予定日は必須です") LocalDate dueDate,
        @Size(max = 200, message = "理由は 200 字までです") String reason) {

    /** WHY: 2 項目にまたがる決まりは項目単位の注釈で書けないため、メソッドで検査する。片方が無い時は @NotNull に任せる。 */
    @AssertTrue(message = "返却予定日は開始日以降にしてください")
    public boolean isDueDateOnOrAfterStartDate() {
        return startDate == null || dueDate == null || !dueDate.isBefore(startDate);
    }
}

/** 一覧の 1 行。actions は今の状態で押せる操作（画面はこれだけを見てボタンを出す）。 */
record RequestView(
        Long requestId,
        String applicantName,
        Long equipmentId,
        String equipmentName,
        LocalDate startDate,
        LocalDate dueDate,
        String reason,
        Status status,
        List<String> actions) {

    static RequestView of(LendingRequest r) {
        List<String> actions = r.getStatus().allowedActions().stream()
                .sorted()
                .map(Action::path)
                .toList();
        return new RequestView(r.getId(), r.getApplicantName(), r.getEquipment().getId(),
                r.getEquipment().getName(), r.getStartDate(), r.getDueDate(), r.getReason(), r.getStatus(), actions);
    }
}

record EquipmentView(Long equipmentId, String name) {
}

/** エラーの応答。details は項目ごとの検査結果（400 の時だけ入る）。 */
record ApiError(int status, String message, List<String> details) {
}
