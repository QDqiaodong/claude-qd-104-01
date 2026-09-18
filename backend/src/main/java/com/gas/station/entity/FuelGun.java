package com.gas.station.entity;

import jakarta.persistence.*;

/** 加油枪：挂在某台加油机上，出某个油品。 */
@Entity
@Table(name = "fuel_gun")
public class FuelGun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 32, unique = true)
    public String code;

    /** 加油机号 */
    @Column(name = "machine_no", nullable = false, length = 16)
    public String machineNo;

    /** 枪出的油品，要和所连储罐一致 */
    @Column(nullable = false, length = 16)
    public String product;

    @Column(name = "tank_id", nullable = false)
    public Long tankId;

    /** 可用 / 停用 / 维修 */
    @Column(nullable = false, length = 16)
    public String status;
}
