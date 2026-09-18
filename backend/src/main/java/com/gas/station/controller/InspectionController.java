package com.gas.station.controller;

import com.gas.station.entity.Inspection;
import com.gas.station.service.InspectionService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class InspectionController {

    private final InspectionService service;

    public InspectionController(InspectionService service) {
        this.service = service;
    }

    @GetMapping("/inspections")
    public List<Inspection> list(
            @RequestParam(required = false) String point,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inspectDate,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String status) {
        return service.list(point, inspectDate, result, status);
    }

    @PostMapping("/inspections")
    public Inspection create(@RequestBody Inspection input) {
        return service.create(input);
    }

    @PostMapping("/inspections/{id}/resolve")
    public Inspection resolve(@PathVariable Long id) {
        return service.resolve(id);
    }
}
