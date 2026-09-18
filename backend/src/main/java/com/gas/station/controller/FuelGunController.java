package com.gas.station.controller;

import com.gas.station.entity.FuelGun;
import com.gas.station.service.FuelGunService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class FuelGunController {

    private final FuelGunService service;

    public FuelGunController(FuelGunService service) {
        this.service = service;
    }

    @GetMapping("/guns")
    public List<FuelGun> list(@RequestParam(required = false) Long tankId,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) String keyword) {
        return service.list(tankId, status, keyword);
    }

    @PostMapping("/guns")
    public FuelGun create(@RequestBody FuelGun input) {
        return service.create(input);
    }

    @PutMapping("/guns/{id}")
    public FuelGun update(@PathVariable Long id, @RequestBody FuelGun input) {
        return service.update(id, input);
    }
}
