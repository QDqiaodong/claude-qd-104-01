package com.gas.station.repository;

import com.gas.station.entity.FuelGun;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FuelGunRepository extends JpaRepository<FuelGun, Long> {

    boolean existsByCode(String code);

    List<FuelGun> findByTankId(Long tankId);

    List<FuelGun> findAllByOrderByIdAsc();

    /** 开班/交班/改枪状态都走这把行锁，同一把枪的事在锁上排队。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from FuelGun g where g.id = :id")
    Optional<FuelGun> findByIdForUpdate(@Param("id") Long id);
}
