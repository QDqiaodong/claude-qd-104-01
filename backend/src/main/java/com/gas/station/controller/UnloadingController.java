package com.gas.station.controller;

import com.gas.station.entity.Unloading;
import com.gas.station.service.UnloadingService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class UnloadingController {

    private final UnloadingService service;

    public UnloadingController(UnloadingService service) {
        this.service = service;
    }

    @GetMapping("/unloadings")
    public List<Unloading> list(@RequestParam(required = false) String status,
                                @RequestParam(required = false) String product,
                                @RequestParam(required = false) Long tankId,
                                @RequestParam(required = false) String keyword) {
        return service.list(status, product, tankId, keyword);
    }

    @PostMapping("/unloadings")
    public Unloading create(@RequestBody Unloading input) {
        return service.create(input);
    }

    @PutMapping("/unloadings/{id}")
    public Unloading update(@PathVariable Long id, @RequestBody Unloading input) {
        return service.update(id, input);
    }

    /** 入罐：服务端复核罐状态、罐容和当天卸油口巡检，全部通过才回写库存 */
    @PostMapping("/unloadings/{id}/enter")
    public Unloading enter(@PathVariable Long id) {
        return service.enter(id);
    }
}
