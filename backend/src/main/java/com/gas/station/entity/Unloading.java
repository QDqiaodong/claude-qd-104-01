package com.gas.station.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 卸油入罐单：槽车到站开单，挂到一口在用储罐上。
 * 开单只占台账，库存不动；单子状态 待入罐 → 已入罐，
 * 只有「入罐」这一下成功，才把计划升数加进罐库存。
 */
@Entity
@Table(name = "unloading", uniqueConstraints = @UniqueConstraint(name = "uk_unload_bill_no", columnNames = "bill_no"))
public class Unloading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 单号，开单时按 XY-yyyyMMdd-序号 生成 */
    @Column(name = "bill_no", nullable = false, length = 32)
    public String billNo;

    /** 槽车车牌 */
    @Column(name = "plate_no", nullable = false, length = 16)
    public String plateNo;

    /** 92# / 95# / 0# / -10#，要和所挂储罐装的油一致 */
    @Column(nullable = false, length = 16)
    public String product;

    /** 计划升数，入罐成功后按这个数加库存，已入罐后不许改 */
    @Column(name = "planned_volume", nullable = false)
    public Integer plannedVolume;

    /** 挂在哪口罐上 */
    @Column(name = "tank_id", nullable = false)
    public Long tankId;

    /** 待入罐 / 已入罐 */
    @Column(nullable = false, length = 16)
    public String status;

    /** 经办人 */
    @Column(length = 32)
    public String operator;

    /** 开单日期 */
    @Column(name = "bill_date", nullable = false)
    public LocalDate billDate;

    /** 真正入罐的时间 */
    @Column(name = "entered_at")
    public LocalDateTime enteredAt;
}
