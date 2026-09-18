package com.gas.station.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/** 罐区巡检：每天看几处点位，有问题的记下来。 */
@Entity
@Table(name = "inspection")
public class Inspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "inspect_date", nullable = false)
    public LocalDate inspectDate;

    /** 巡检点位，例如 T-01罐区 / 卸油口 */
    @Column(nullable = false, length = 32)
    public String point;

    /** 正常 / 异常 */
    @Column(nullable = false, length = 16)
    public String result;

    @Column(name = "issue_desc", length = 255)
    public String issueDesc;

    @Column(nullable = false, length = 32)
    public String inspector;

    /** 已记录 / 已处理 */
    @Column(nullable = false, length = 16)
    public String status;
}
