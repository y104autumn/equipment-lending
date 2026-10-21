package com.example.lending.request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 状態の履歴。追記のみ（更新・削除しない）。changed_at は DB の既定値に任せる。 */
@Entity
@Table(name = "status_history")
public class StatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    /** null＝申請した時（前の状態が無い）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 10)
    private Status fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 10)
    private Status toStatus;

    protected StatusHistory() {
    }

    public StatusHistory(Long requestId, Status fromStatus, Status toStatus) {
        this.requestId = requestId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public Status getFromStatus() {
        return fromStatus;
    }

    public Status getToStatus() {
        return toStatus;
    }
}
