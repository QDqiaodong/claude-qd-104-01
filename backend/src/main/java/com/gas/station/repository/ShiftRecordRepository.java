package com.gas.station.repository;

import com.gas.station.entity.ShiftRecord;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftRecordRepository extends JpaRepository<ShiftRecord, Long> {

    List<ShiftRecord> findByShiftDateAndShiftType(LocalDate shiftDate, String shiftType);

    List<ShiftRecord> findAllByOrderByIdDesc();
}
