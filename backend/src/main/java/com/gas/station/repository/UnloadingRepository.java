package com.gas.station.repository;

import com.gas.station.entity.Unloading;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnloadingRepository extends JpaRepository<Unloading, Long> {

    List<Unloading> findAllByOrderByIdDesc();

    long countByBillDate(LocalDate billDate);

    /** 入罐时先把单据行锁住，同一张单的入罐/编辑互相串行。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Unloading u where u.id = :id")
    Optional<Unloading> findByIdForUpdate(@Param("id") Long id);
}
