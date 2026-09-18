package com.gas.station.repository;

import com.gas.station.entity.Tank;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TankRepository extends JpaRepository<Tank, Long> {

    boolean existsByCode(String code);

    List<Tank> findByStatus(String status);

    List<Tank> findAllByOrderByIdAsc();

    /** 入罐时锁罐行，库存的读-校验-加必须在同一把行锁里完成。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tank t where t.id = :id")
    Optional<Tank> findByIdForUpdate(@Param("id") Long id);
}
