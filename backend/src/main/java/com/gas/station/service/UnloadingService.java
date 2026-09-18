package com.gas.station.service;

import com.gas.station.dto.BizException;
import com.gas.station.entity.Tank;
import com.gas.station.entity.Unloading;
import com.gas.station.repository.InspectionRepository;
import com.gas.station.repository.TankRepository;
import com.gas.station.repository.UnloadingRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnloadingService {

    /** 卸油口巡检点位，入罐前要查它当天有没有没处理完的异常 */
    public static final String UNLOAD_POINT = "卸油口";

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final UnloadingRepository unloadings;
    private final TankRepository tanks;
    private final InspectionRepository inspections;
    private final UnloadingService self;

    public UnloadingService(UnloadingRepository unloadings, TankRepository tanks,
                            InspectionRepository inspections, @Lazy UnloadingService self) {
        this.unloadings = unloadings;
        this.tanks = tanks;
        this.inspections = inspections;
        this.self = self;
    }

    public List<Unloading> list(String status, String product, Long tankId, String keyword) {
        return unloadings.findAllByOrderByIdDesc().stream()
                .filter(u -> status == null || status.isEmpty() || status.equals(u.status))
                .filter(u -> product == null || product.isEmpty() || product.equals(u.product))
                .filter(u -> tankId == null || tankId.equals(u.tankId))
                .filter(u -> keyword == null || keyword.isBlank()
                        || (u.plateNo != null && u.plateNo.contains(keyword.trim()))
                        || (u.billNo != null && u.billNo.contains(keyword.trim())))
                .toList();
    }

    /** 非事务编排：两个站员同时开单可能算出同一个序号，撞唯一键就重取序号再来一次。 */
    public Unloading create(Unloading input) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return self.doCreate(input);
            } catch (DataIntegrityViolationException ex) {
                // 单号唯一键撞了，下一轮重新 count 当天单数再试
            }
        }
        throw new BizException("开单太频繁撞了单号，请再点一次");
    }

    @Transactional
    public Unloading doCreate(Unloading input) {
        Tank tank = checkAndLoadTank(input);
        LocalDate today = LocalDate.now(ZONE);
        Unloading u = new Unloading();
        u.plateNo = input.plateNo.trim();
        u.product = input.product;
        u.plannedVolume = input.plannedVolume;
        u.tankId = tank.id;
        u.operator = blankToNull(input.operator);
        u.billDate = today;
        u.status = "待入罐";
        u.billNo = nextBillNo(today);
        return unloadings.save(u);
    }

    /** 单号 XY-yyyyMMdd-序号；序号按当天已有单数 +1，撞唯一键时让调用方重试。 */
    private String nextBillNo(LocalDate today) {
        return String.format("XY-%tY%<tm%<td-%03d", today, unloadings.countByBillDate(today) + 1);
    }

    @Transactional
    public Unloading update(Long id, Unloading input) {
        Unloading u = unloadings.findById(id).orElseThrow(() -> new BizException("卸油单不存在"));
        if ("已入罐".equals(u.status)) {
            // 已经入罐的单，升数和所挂的罐都钉死了，整张单不再改
            throw new BizException("这张单已经入罐，升数和所挂的罐都不能再改");
        }
        Tank tank = checkAndLoadTank(input);
        u.plateNo = input.plateNo.trim();
        u.product = input.product;
        u.plannedVolume = input.plannedVolume;
        u.tankId = tank.id;
        if (input.operator != null) {
            u.operator = blankToNull(input.operator);
        }
        return unloadings.save(u);
    }

    /**
     * 入罐：单子停在待入罐期间库存一律不动，只有这一下成功才加库存。
     * 行锁顺序固定为「先锁单、再锁罐」，同罐并发入罐在罐行锁上串行，
     * 后进来的那个看到的是加完后的库存，超罐容就整体失败、回滚。
     */
    @Transactional
    public Unloading enter(Long id) {
        // 1. 先锁单，防止同一张单被重复入罐或边改边入
        Unloading u = unloadings.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("卸油单不存在"));
        if (!"待入罐".equals(u.status)) {
            throw new BizException("这张单已经入过罐了");
        }

        // 2. 安全员拍板的闸门：当天卸油口巡检还停在待处理，不许入罐，库存不动
        LocalDate today = LocalDate.now(ZONE);
        boolean gateBlocked = inspections.findByPointAndInspectDate(UNLOAD_POINT, today).stream()
                .anyMatch(i -> "待处理".equals(i.status));
        if (gateBlocked) {
            throw new BizException("今天「卸油口」巡检还有异常待处理，处理完才能入罐");
        }

        // 3. 再锁罐，库存的读-校验-加都在这把行锁里
        Tank tank = tanks.findByIdForUpdate(u.tankId)
                .orElseThrow(() -> new BizException("所挂的储罐不存在"));
        if (!"在用".equals(tank.status)) {
            // 点入罐时罐可能已经被改成检修了
            throw new BizException(tank.code + " 现在是检修状态，接不了卸，这次入罐作废");
        }
        if (!u.product.equals(tank.product)) {
            throw new BizException("单上油品是 " + u.product + "，" + tank.code
                    + " 现在装的是 " + tank.product + "，对不上");
        }
        int after = tank.stock + u.plannedVolume;
        if (after > tank.capacity) {
            // 库存可能刚被别人加过，再加就超罐容：失败，库存保持点之前的数
            throw new BizException(tank.code + " 此刻库存 " + tank.stock + " 升，再卸 "
                    + u.plannedVolume + " 升就到 " + after + " 升，超过罐容 "
                    + tank.capacity + " 升");
        }

        // 4. 全部通过，库存和单据状态在同一事务里落库
        tank.stock = after;
        tanks.save(tank);
        u.status = "已入罐";
        u.enteredAt = LocalDateTime.now(ZONE);
        return unloadings.save(u);
    }

    /** 开单/改单的共同校验：罐存在且在用、油品对口、升数为正且不超罐容。 */
    private Tank checkAndLoadTank(Unloading input) {
        if (input.plateNo == null || input.plateNo.isBlank()) {
            throw new BizException("要填槽车车牌");
        }
        if (input.product == null || input.product.isBlank()) {
            throw new BizException("要选槽车拉的油品");
        }
        if (input.plannedVolume == null || input.plannedVolume <= 0) {
            throw new BizException("计划升数要大于 0");
        }
        if (input.tankId == null) {
            throw new BizException("要挂一口现有储罐");
        }
        Tank tank = tanks.findById(input.tankId)
                .orElseThrow(() -> new BizException("所挂的储罐不存在"));
        if (!"在用".equals(tank.status)) {
            throw new BizException(tank.code + " 正在检修，接不了卸，挂到在用的罐上");
        }
        if (!input.product.equals(tank.product)) {
            throw new BizException("这槽车是 " + input.product + "，" + tank.code
                    + " 装的是 " + tank.product + "，油品不一致不能挂");
        }
        if (tank.stock + input.plannedVolume > tank.capacity) {
            throw new BizException(tank.code + " 此刻库存 " + tank.stock + " 升，计划卸 "
                    + input.plannedVolume + " 升会到 " + (tank.stock + input.plannedVolume)
                    + " 升，超过罐容 " + tank.capacity + " 升");
        }
        return tank;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
