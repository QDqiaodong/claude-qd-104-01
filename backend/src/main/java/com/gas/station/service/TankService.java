package com.gas.station.service;

import com.gas.station.dto.BizException;
import com.gas.station.entity.FuelGun;
import com.gas.station.entity.Tank;
import com.gas.station.repository.FuelGunRepository;
import com.gas.station.repository.TankRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TankService {

    private final TankRepository tanks;
    private final FuelGunRepository guns;

    public TankService(TankRepository tanks, FuelGunRepository guns) {
        this.tanks = tanks;
        this.guns = guns;
    }

    public List<Tank> list(String status, String product, String keyword) {
        return tanks.findAllByOrderByIdAsc().stream()
                .filter(t -> status == null || status.isEmpty() || status.equals(t.status))
                .filter(t -> product == null || product.isEmpty() || product.equals(t.product))
                .filter(t -> keyword == null || keyword.isEmpty()
                        || t.code.contains(keyword) || t.product.contains(keyword))
                .toList();
    }

    @Transactional
    public Tank create(Tank input) {
        if (input.code == null || input.code.isBlank()) {
            throw new BizException("罐号不能为空");
        }
        if (tanks.existsByCode(input.code)) {
            throw new BizException("罐号 " + input.code + " 已经用过了");
        }
        if (input.capacity == null || input.capacity <= 0) {
            throw new BizException("罐容要大于 0 升");
        }
        if (input.stock != null && (input.stock < 0 || input.stock > input.capacity)) {
            throw new BizException("当前库存要在 0 到罐容之间");
        }
        Tank saved = new Tank();
        saved.code = input.code.trim();
        saved.product = (input.product == null || input.product.isBlank()) ? "92#" : input.product;
        saved.capacity = input.capacity;
        saved.stock = input.stock == null ? 0 : input.stock;
        saved.safeStock = (input.safeStock == null || input.safeStock <= 0) ? 3000 : input.safeStock;
        saved.status = (input.status == null || input.status.isBlank()) ? "在用" : input.status;
        return tanks.save(saved);
    }

    @Transactional
    public Tank update(Long id, Tank input) {
        Tank t = tanks.findById(id).orElseThrow(() -> new BizException("储罐不存在"));
        List<FuelGun> attached = guns.findByTankId(t.id);
        if (input.capacity != null && !input.capacity.equals(t.capacity)) {
            if (input.capacity <= 0) {
                throw new BizException("罐容要大于 0 升");
            }
            if (input.capacity < t.stock) {
                throw new BizException("罐里还有 " + t.stock + " 升油，罐容不能改到比它小");
            }
            t.capacity = input.capacity;
        }
        if (input.stock != null && !input.stock.equals(t.stock)) {
            if (input.stock < 0 || input.stock > t.capacity) {
                throw new BizException("当前库存要在 0 到 " + t.capacity + " 升之间");
            }
            t.stock = input.stock;
        }
        if (input.safeStock != null && input.safeStock > 0
                && !input.safeStock.equals(t.safeStock)) {
            t.safeStock = input.safeStock;
        }
        if (input.product != null && !input.product.isBlank() && !input.product.equals(t.product)) {
            if (!attached.isEmpty()) {
                throw new BizException("这个罐还挂着 " + attached.size()
                        + " 把枪，先改枪的油品或者摘下来才能换罐里的油品");
            }
            t.product = input.product;
        }
        if (input.status != null && !input.status.isBlank() && !input.status.equals(t.status)) {
            if ("检修".equals(input.status) && !attached.isEmpty()) {
                throw new BizException("这个罐还挂着 " + attached.size() + " 把枪，先摘下来才能转检修");
            }
            t.status = input.status;
        }
        return tanks.save(t);
    }
}
