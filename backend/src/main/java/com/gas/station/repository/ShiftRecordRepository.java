package com.gas.station.repository;

import com.gas.station.entity.ShiftRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftRecordRepository extends JpaRepository<ShiftRecord, Long> {

    List<ShiftRecord> findByShiftDateAndShiftType(LocalDate shiftDate, String shiftType);

    List<ShiftRecord> findAllByOrderByIdDesc();

    boolean existsByGunIdAndStatus(Long gunId, String status);

    /** 交班/改班时先锁班行，同一个班的交班、改班互相串行。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ShiftRecord s where s.id = :id")
    Optional<ShiftRecord> findByIdForUpdate(@Param("id") Long id);
}
