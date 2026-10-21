package com.example.lending.request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 貸出申請。created_at は DB の既定値（now()）に任せるためマッピングしない。 */
@Entity
@Table(name = "lending_request")
public class LendingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long id;

    @Column(name = "applicant_name", nullable = false, length = 50)
    private String applicantName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "reason", length = 200)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    /** WHY: 楽観ロック。総務 2 人が同じ申請を同時に操作した時、後の方を 409 にする（テーブル定義書）。 */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected LendingRequest() {
    }

    public LendingRequest(String applicantName, Equipment equipment, LocalDate startDate, LocalDate dueDate,
            String reason) {
        this.applicantName = applicantName;
        this.equipment = equipment;
        this.startDate = startDate;
        this.dueDate = dueDate;
        this.reason = reason;
        this.status = Status.REQUESTED;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 状態を進める。状態の表に無い操作なら IllegalTransitionException。
     *
     * @return 変わる前の状態（履歴に残すため）
     */
    Status apply(Action action) {
        Status from = status;
        status = from.next(action).orElseThrow(() -> new IllegalTransitionException(id, from, action));
        updatedAt = OffsetDateTime.now();
        return from;
    }

    public Long getId() {
        return id;
    }

    public String getApplicantName() {
        return applicantName;
    }

    public Equipment getEquipment() {
        return equipment;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getReason() {
        return reason;
    }

    public Status getStatus() {
        return status;
    }
}
