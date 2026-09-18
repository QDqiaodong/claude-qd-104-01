package com.gas.station.controller;

import com.gas.station.entity.Tank;
import com.gas.station.service.TankService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TankController {

    private final TankService service;

    public TankController(TankService service) {
        this.service = service;
    }

    @GetMapping("/tanks")
    public List<Tank> list(@RequestParam(required = false) String status,
                           @RequestParam(required = false) String product,
                           @RequestParam(required = false) String keyword) {
        return service.list(status, product, keyword);
    }

    @PostMapping("/tanks")
    public Tank create(@RequestBody Tank input) {
        return service.create(input);
    }

    @PutMapping("/tanks/{id}")
    public Tank update(@PathVariable Long id, @RequestBody Tank input) {
        return service.update(id, input);
    }
}
