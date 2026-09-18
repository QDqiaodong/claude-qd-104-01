package com.gas.station.service;

import com.gas.station.dto.BizException;
import com.gas.station.entity.FuelGun;
import com.gas.station.entity.Tank;
import com.gas.station.repository.FuelGunRepository;
import com.gas.station.repository.ShiftRecordRepository;
import com.gas.station.repository.TankRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FuelGunService {

    /** 枪状态只认这三个值 */
    static final List<String> STATUSES = List.of("可用", "停用", "维修");

    private final FuelGunRepository guns;
    private final TankRepository tanks;
    private final ShiftRecordRepository shifts;

    public FuelGunService(FuelGunRepository guns, TankRepository tanks,
                          ShiftRecordRepository shifts) {
        this.guns = guns;
        this.tanks = tanks;
        this.shifts = shifts;
    }

    public List<FuelGun> list(Long tankId, String status, String keyword) {
        return guns.findAllByOrderByIdAsc().stream()
                .filter(g -> tankId == null || tankId.equals(g.tankId))
                .filter(g -> status == null || status.isEmpty() || status.equals(g.status))
                .filter(g -> keyword == null || keyword.isEmpty()
                        || g.code.contains(keyword) || g.machineNo.contains(keyword))
                .toList();
    }

    /** 枪和罐必须一个油品，而且罐得是在用的。 */
    private void checkTank(FuelGun input) {
        Tank tank = tanks.findById(input.tankId)
                .orElseThrow(() -> new BizException("储罐不存在"));
        if ("检修".equals(tank.status)) {
            throw new BizException("储罐 " + tank.code + " 正在检修，不能挂枪");
        }
        if (input.product != null && !input.product.equals(tank.product)) {
            throw new BizException("储罐 " + tank.code + " 装的是 " + tank.product
                    + "，挂不了出 " + input.product + " 的枪");
        }
    }

    @Transactional
    public FuelGun create(FuelGun input) {
        if (input.code == null || input.code.isBlank()) {
            throw new BizException("枪号不能为空");
        }
        if (guns.existsByCode(input.code)) {
            throw new BizException("枪号 " + input.code + " 已经用过了");
        }
        if (input.tankId == null) {
            throw new BizException("请选择这把枪连哪个储罐");
        }
        if (input.machineNo == null || input.machineNo.isBlank()) {
            throw new BizException("要填加油机号");
        }
        if (input.status != null && !input.status.isBlank() && !STATUSES.contains(input.status)) {
            throw new BizException("枪的状态只能是 可用 / 停用 / 维修");
        }
        checkTank(input);
        FuelGun saved = new FuelGun();
        saved.code = input.code.trim();
        saved.machineNo = input.machineNo.trim();
        saved.product = input.product;
        saved.tankId = input.tankId;
        saved.status = (input.status == null || input.status.isBlank()) ? "可用" : input.status;
        return guns.save(saved);
    }

    /**
     * 改枪：全程捏着枪行锁。要改成维修/停用时，这把枪上不能还挂着当班中的班——
     * 不然机修进了场，当班本子还按老读数往外交，这班油量就对不上是哪把枪跑的了。
     * 和交班撞上的话，两边在这把枪的行锁上串行，只许一边先生效。
     */
    @Transactional
    public FuelGun update(Long id, FuelGun input) {
        FuelGun g = guns.findByIdForUpdate(id).orElseThrow(() -> new BizException("油枪不存在"));
        if (input.machineNo != null && !input.machineNo.isBlank()) {
            g.machineNo = input.machineNo.trim();
        }
        boolean tankChanged = input.tankId != null && !input.tankId.equals(g.tankId);
        boolean productChanged = input.product != null && !input.product.isBlank()
                && !input.product.equals(g.product);
        if (tankChanged || productChanged) {
            FuelGun probe = new FuelGun();
            probe.tankId = tankChanged ? input.tankId : g.tankId;
            probe.product = productChanged ? input.product : g.product;
            checkTank(probe);
            g.tankId = probe.tankId;
            g.product = probe.product;
        }
        if (input.status != null && !input.status.isBlank() && !input.status.equals(g.status)) {
            if (!STATUSES.contains(input.status)) {
                throw new BizException("枪的状态只能是 可用 / 停用 / 维修");
            }
            if (("维修".equals(input.status) || "停用".equals(input.status))
                    && shifts.existsByGunIdAndStatus(g.id, "当班中")) {
                throw new BizException("枪 " + g.code + " 还挂在当班中的班上，先交班才能改成"
                        + input.status);
            }
            g.status = input.status;
        }
        return guns.save(g);
    }
}
