package com.gas.station.repository;

import com.gas.station.entity.FuelGun;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FuelGunRepository extends JpaRepository<FuelGun, Long> {

    boolean existsByCode(String code);

    List<FuelGun> findByTankId(Long tankId);

    List<FuelGun> findAllByOrderByIdAsc();
}
