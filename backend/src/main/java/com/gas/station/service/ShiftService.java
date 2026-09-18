package com.gas.station.service;

import com.gas.station.dto.BizException;
import com.gas.station.entity.ShiftRecord;
import com.gas.station.repository.ShiftRecordRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShiftService {

    private final ShiftRecordRepository shifts;

    public ShiftService(ShiftRecordRepository shifts) {
        this.shifts = shifts;
    }

    public List<ShiftRecord> list(LocalDate shiftDate, String shiftType, String status) {
        return shifts.findAllByOrderByIdDesc().stream()
                .filter(s -> shiftDate == null || shiftDate.equals(s.shiftDate))
                .filter(s -> shiftType == null || shiftType.isEmpty()
                        || shiftType.equals(s.shiftType))
                .filter(s -> status == null || status.isEmpty() || status.equals(s.status))
                .toList();
    }

    @Transactional
    public ShiftRecord create(ShiftRecord input) {
        if (input.shiftDate == null) {
            throw new BizException("请选择班次日期");
        }
        if (input.shiftType == null || input.shiftType.isBlank()) {
            throw new BizException("请选择白班还是夜班");
        }
        if (input.startReading == null || input.startReading < 0) {
            throw new BizException("接班的枪读数不能空，也不能是负数");
        }
        if (!shifts.findByShiftDateAndShiftType(input.shiftDate, input.shiftType).isEmpty()) {
            throw new BizException(input.shiftDate + " 的" + input.shiftType
                    + "已经开过了，一天一个班次只开一次");
        }
        ShiftRecord saved = new ShiftRecord();
        saved.shiftDate = input.shiftDate;
        saved.shiftType = input.shiftType;
        saved.startReading = input.startReading;
        saved.operator = input.operator;
        saved.status = "当班中";
        return shifts.save(saved);
    }

    @Transactional
    public ShiftRecord handover(Long id, Integer endReading, Integer amount) {
        ShiftRecord shift = shifts.findById(id).orElseThrow(() -> new BizException("班次不存在"));
        if (!"当班中".equals(shift.status)) {
            throw new BizException("这个班已经交接过库里了");
        }
        if (endReading == null) {
            throw new BizException("交班要填交班时的枪读数");
        }
        if (endReading < shift.startReading) {
            throw new BizException("交班读数不能小于接班读数 " + shift.startReading);
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
