package com.gas.station.entity;

import jakarta.persistence.*;

/** 储罐：地下的油罐，装一种油品。 */
@Entity
@Table(name = "tank")
public class Tank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 32, unique = true)
    public String code;

    /** 92# / 95# / 0# / -10# */
    @Column(nullable = false, length = 16)
    public String product;

    /** 罐容，升 */
    @Column(nullable = false)
    public Integer capacity;

    /** 当前库存，升 */
    @Column(nullable = false)
    public Integer stock;

    /** 低于这个量要提醒补油 */
    @Column(name = "safe_stock", nullable = false)
    public Integer safeStock;

    /** 在用 / 检修 */
    @Column(nullable = false, length = 16)
    public String status;
}
