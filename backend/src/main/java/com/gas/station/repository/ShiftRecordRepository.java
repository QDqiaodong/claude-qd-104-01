package com.gas.station.repository;

import com.gas.station.entity.ShiftRecord;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftRecordRepository extends JpaRepository<ShiftRecord, Long> {

    List<ShiftRecord> findByShiftDateAndShiftType(LocalDate shiftDate, String shiftType);

    List<ShiftRecord> findByGunIdAndStatus(Long gunId, String status);

    List<ShiftRecord> findAllByOrderByIdDesc();

    /** 交班时锁班行，防止同一个班被两个人同时交。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ShiftRecord s where s.id = :id")
    Optional<ShiftRecord> findByIdForUpdate(@Param("id") Long id);
}
