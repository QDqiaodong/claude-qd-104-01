package com.gas.station.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/** 班次交接：一个班的加油量与收款。 */
@Entity
@Table(name = "shift_record")
public class ShiftRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "shift_date", nullable = false)
    public LocalDate shiftDate;

    /** 白班 / 夜班 */
    @Column(name = "shift_type", nullable = false, length = 16)
    public String shiftType;

    /** 这个班挂在哪把枪上，开班时选定后不再改 */
    @Column(name = "gun_id", nullable = false)
    public Long gunId;

    /** 接班时的枪读数 */
    @Column(name = "start_reading", nullable = false)
    public Integer startReading;

    /** 交班时的枪读数，还没交班时为空 */
    @Column(name = "end_reading")
    public Integer endReading;

    /** 本班加油量（升），交班时由服务端算 */
    @Column
    public Integer volume;

    /** 本班收款金额，元 */
    @Column
    public Integer amount;

    @Column(nullable = false, length = 32)
    public String operator;

    /** 当班中 / 已交接 */
    @Column(nullable = false, length = 16)
    public String status;
}
