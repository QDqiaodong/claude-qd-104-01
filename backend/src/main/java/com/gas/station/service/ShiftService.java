package com.gas.station.service;

import com.gas.station.dto.BizException;
import com.gas.station.entity.FuelGun;
import com.gas.station.entity.ShiftRecord;
import com.gas.station.repository.FuelGunRepository;
import com.gas.station.repository.ShiftRecordRepository;
import java.time.LocalDate;
import java.util.List;
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
     * 开班：必须选定一把当前可用的枪，接班读数记在这把枪上。
     * 枪行先锁再校验，开班和「改维修/停用」在这把锁上排队，不会各改各的。
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
            throw new BizException("开班要选定一把加油枪");
        }
        if (input.startReading == null || input.startReading < 0) {
            throw new BizException("接班的枪读数不能空，也不能是负数");
        }
        if (input.operator == null || input.operator.isBlank()) {
            throw new BizException("要填当班人");
        }
        if (!shifts.findByShiftDateAndShiftType(input.shiftDate, input.shiftType).isEmpty()) {
            throw new BizException(input.shiftDate + " 的" + input.shiftType
                    + "已经开过了，一天一个班次只开一次");
        }
        FuelGun gun = guns.findByIdForUpdate(input.gunId)
                .orElseThrow(() -> new BizException("选的油枪不存在"));
        if (!"可用".equals(gun.status)) {
            throw new BizException(gun.code + " 现在是「" + gun.status
                    + "」，开不了班，换一把可用的枪");
        }
        if (!shifts.findByGunIdAndStatus(gun.id, "当班中").isEmpty()) {
            throw new BizException(gun.code + " 上还有一个班没交，一把枪同时只跑一个班");
        }
        ShiftRecord saved = new ShiftRecord();
        saved.shiftDate = input.shiftDate;
        saved.shiftType = input.shiftType;
        saved.gunId = gun.id;
        saved.startReading = input.startReading;
        saved.operator = input.operator.trim();
        saved.status = "当班中";
        return shifts.save(saved);
    }

    /**
     * 交班：先锁班、再锁枪。点交班这一刻枪已被人改成维修/停用的，
     * 这次交班整体失败回滚，班停在当班中，不会变成已交接。
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
        FuelGun gun = guns.findByIdForUpdate(shift.gunId)
                .orElseThrow(() -> new BizException("这个班挂的油枪不存在了"));
        if (!"可用".equals(gun.status)) {
            throw new BizException(gun.code + " 现在是「" + gun.status
                    + "」，这个班交不出去，先把枪恢复可用再交");
        }
        shift.endReading = endReading;
        shift.volume = endReading - shift.startReading;
        shift.amount = amount;
        shift.status = "已交接";
        return shifts.save(shift);
    }

    @Transactional
    public ShiftRecord update(Long id, ShiftRecord input) {
        ShiftRecord shift = shifts.findById(id).orElseThrow(() -> new BizException("班次不存在"));
        if ("已交接".equals(shift.status)) {
            throw new BizException("这个班已经交接了，改不了");
        }
        if (input.gunId != null && !input.gunId.equals(shift.gunId)) {
            throw new BizException("班所挂的枪开班时就定了，改不了");
        }
        if (input.operator != null && !input.operator.isBlank()) {
            shift.operator = input.operator;
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
