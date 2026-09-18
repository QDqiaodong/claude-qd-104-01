package com.gas.station.controller;

import com.gas.station.entity.ShiftRecord;
import com.gas.station.service.ShiftService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ShiftController {

    private final ShiftService service;

    public ShiftController(ShiftService service) {
        this.service = service;
    }

    @GetMapping("/shifts")
    public List<ShiftRecord> list(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate shiftDate,
            @RequestParam(required = false) String shiftType,
            @RequestParam(required = false) String status) {
        return service.list(shiftDate, shiftType, status);
    }

    @PostMapping("/shifts")
    public ShiftRecord create(@RequestBody ShiftRecord input) {
        return service.create(input);
    }

    @PutMapping("/shifts/{id}")
    public ShiftRecord update(@PathVariable Long id, @RequestBody ShiftRecord input) {
        return service.update(id, input);
    }

    @PostMapping("/shifts/{id}/handover")
    public ShiftRecord handover(@PathVariable Long id,
                                @RequestParam Integer endReading,
                                @RequestParam(required = false) Integer amount) {
        return service.handover(id, endReading, amount);
    }
}
