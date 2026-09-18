package com.gas.station.repository;

import com.gas.station.entity.Inspection;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionRepository extends JpaRepository<Inspection, Long> {

    List<Inspection> findByPointAndInspectDate(String point, LocalDate inspectDate);

    List<Inspection> findAllByOrderByIdDesc();
}
