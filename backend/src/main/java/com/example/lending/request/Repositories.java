package com.example.lending.request;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    List<Equipment> findAllByOrderByIdAsc();
}

interface LendingRequestRepository extends JpaRepository<LendingRequest, Long> {

    /** WHY: 一覧は備品名も出すので join fetch で 1 回に取る（open-in-view を切っているため遅延ロードできない）。 */
    @Query("select r from LendingRequest r join fetch r.equipment"
            + " where (:status is null or r.status = :status) order by r.id desc")
    List<LendingRequest> findForList(@Param("status") Status status);
}

interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    List<StatusHistory> findByRequestIdOrderByIdAsc(Long requestId);
}
