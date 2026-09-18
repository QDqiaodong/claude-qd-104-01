package com.gas.station.service;

import com.gas.station.dto.BizException;
import com.gas.station.entity.FuelGun;
import com.gas.station.entity.ShiftRecord;
import com.gas.station.repository.FuelGunRepository;
import com.gas.station.repository.ShiftRecordRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShiftService {

    private final ShiftRecordRepository shifts;
    private final FuelGunRepository guns;

    public ShiftService(ShiftRecordRepository shifts, FuelGunRepository guns) {
        this.shifts = shifts;
        this.guns = guns;
    }

    public List<ShiftRecord> list(LocalDate shiftDate, String shiftType, String status) {
        return shifts.findAllByOrderByIdDesc().stream()
                .filter(s -> shiftDate == null || shiftDate.equals(s.shiftDate))
                .filter(s -> shiftType == null || shiftType.isEmpty()
                        || shiftType.equals(s.shiftType))
                .filter(s -> status == null || status.isEmpty() || status.equals(s.status))
                .toList();
    }

    /**
     * 开班：在枪行锁里核对「枪存在、此刻可用、这把枪没有没交的班」，
     * 接班读数记在这把枪上。同一天同一个班次的唯一键兜底并发撞班。
     */
    @Transactional
    public ShiftRecord create(ShiftRecord input) {
        if (input.shiftDate == null) {
            throw new BizException("请选择班次日期");
        }
        if (input.shiftType == null || input.shiftType.isBlank()) {
            throw new BizException("请选择白班还是夜班");
        }
        if (input.gunId == null) {
            throw new BizException("开班要选定一把当前可用的枪");
        }
        if (input.startReading == null || input.startReading < 0) {
            throw new BizException("接班的枪读数不能空，也不能是负数");
        }
        if (input.operator == null || input.operator.isBlank()) {
            throw new BizException("要填当班人");
        }
        // 先锁枪行：和「改枪状态」「交班」在同一把锁上串行
        FuelGun gun = guns.findByIdForUpdate(input.gunId)
                .orElseThrow(() -> new BizException("油枪不存在"));
        if (!"可用".equals(gun.status)) {
            throw new BizException("枪 " + gun.code + " 现在是" + gun.status
                    + "状态，开不了班，换一把可用的枪");
        }
        if (shifts.existsByGunIdAndStatus(gun.id, "当班中")) {
            throw new BizException("枪 " + gun.code + " 上还有一个班没交，先交完才能再开");
        }
        if (!shifts.findByShiftDateAndShiftType(input.shiftDate, input.shiftType).isEmpty()) {
            throw new BizException(input.shiftDate + " 的" + input.shiftType
                    + "已经开过了，一天一个班次只开一次");
        }
        ShiftRecord saved = new ShiftRecord();
        saved.shiftDate = input.shiftDate;
        saved.shiftType = input.shiftType;
        saved.gunId = gun.id;
        saved.startReading = input.startReading;
        saved.operator = input.operator.trim();
        saved.status = "当班中";
        try {
            return shifts.saveAndFlush(saved);
        } catch (DataIntegrityViolationException ex) {
            // 两把枪同时开同一天同一个班，撞唯一键的那个在这里拦下
            throw new BizException(input.shiftDate + " 的" + input.shiftType
                    + "已经开过了，一天一个班次只开一次");
        }
    }

    /**
     * 交班：先锁班行（同一个班的交班/改班串行），再锁枪行核对枪此刻的状态。
     * 点交班的同时枪被改成维修/停用的，这次交班失败，班还停在当班中，
     * 不会变成已交接；等枪恢复可用再交。
     */
    @Transactional
    public ShiftRecord handover(Long id, Integer endReading, Integer amount) {
        ShiftRecord shift = shifts.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("班次不存在"));
        if (!"当班中".equals(shift.status)) {
            throw new BizException("这个班已经交接过库里了");
        }
        if (endReading == null) {
            throw new BizException("交班要填交班时的枪读数");
        }
        if (endReading < shift.startReading) {
            throw new BizException("交班读数不能小于接班读数 " + shift.startReading);
        }
        // 锁枪行：看到的一定是这一刻已提交的状态，不会照着旧页面把班交出去
        FuelGun gun = guns.findByIdForUpdate(shift.gunId)
                .orElseThrow(() -> new BizException("这个班挂的油枪不存在"));
        if (!"可用".equals(gun.status)) {
            throw new BizException("枪 " + gun.code + " 现在是" + gun.status
                    + "状态，这次交班作废，班还停在当班中，等枪恢复可用再交");
        }
        shift.endReading = endReading;
        shift.volume = endReading - shift.startReading;
        shift.amount = amount;
        shift.status = "已交接";
        return shifts.save(shift);
    }

    /**
     * 改班：只允许还没交接的班改当班人和接班读数；
     * 已经交接的班，所挂的枪和两头读数都锁死，整班不再动。
     */
    @Transactional
    public ShiftRecord update(Long id, ShiftRecord input) {
        ShiftRecord shift = shifts.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("班次不存在"));
        if ("已交接".equals(shift.status)) {
            throw new BizException("这个班已经交接了，改不了");
        }
        if (input.operator != null && !input.operator.isBlank()) {
            shift.operator = input.operator.trim();
        }
        if (input.startReading != null && !input.startReading.equals(shift.startReading)) {
            if (input.startReading < 0) {
                throw new BizException("接班的枪读数不能是负数");
            }
            shift.startReading = input.startReading;
        }
        return shifts.save(shift);
    }
}
