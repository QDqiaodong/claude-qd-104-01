package com.gas.station.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gas.station.dto.BizException;
import com.gas.station.entity.FuelGun;
import com.gas.station.entity.ShiftRecord;
import com.gas.station.entity.Tank;
import com.gas.station.repository.FuelGunRepository;
import com.gas.station.repository.ShiftRecordRepository;
import com.gas.station.repository.TankRepository;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ShiftServiceTest {

    @Autowired
    private ShiftService service;
    @Autowired
    private FuelGunService gunService;
    @Autowired
    private ShiftRecordRepository shifts;
    @Autowired
    private FuelGunRepository guns;
    @Autowired
    private TankRepository tanks;

    private Long usableGun;
    private Long repairGun;
    private Long stoppedGun;

    @BeforeEach
    void setUp() {
        shifts.deleteAll();
        guns.deleteAll();
        tanks.deleteAll();

        Tank t = new Tank();
        t.code = "T-01";
        t.product = "92#";
        t.capacity = 30000;
        t.stock = 13000;
        t.safeStock = 5000;
        t.status = "在用";
        tanks.save(t);

        usableGun = saveGun("G-01", "可用", t.id).id;
        repairGun = saveGun("G-02", "维修", t.id).id;
        stoppedGun = saveGun("G-03", "停用", t.id).id;
    }

    private FuelGun saveGun(String code, String status, Long tankId) {
        FuelGun g = new FuelGun();
        g.code = code;
        g.machineNo = "1号机";
        g.product = "92#";
        g.tankId = tankId;
        g.status = status;
        return guns.save(g);
    }

    private ShiftRecord openInput(Long gunId, String shiftType, int startReading) {
        ShiftRecord s = new ShiftRecord();
        s.shiftDate = LocalDate.of(2026, 9, 18);
        s.shiftType = shiftType;
        s.gunId = gunId;
        s.startReading = startReading;
        s.operator = "测试员";
        return s;
    }

    private FuelGun statusInput(String status) {
        FuelGun g = new FuelGun();
        g.status = status;
        return g;
    }

    @Test
    void open_requiresGun() {
        assertThatThrownBy(() -> service.create(openInput(null, "白班", 1000)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("加油枪");
    }

    @Test
    void open_rejectsGunNotUsable() {
        assertThatThrownBy(() -> service.create(openInput(repairGun, "白班", 1000)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("开不了班");
        assertThatThrownBy(() -> service.create(openInput(stoppedGun, "白班", 1000)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("开不了班");
    }

    @Test
    void open_rejectsSecondActiveShiftOnSameGun() {
        service.create(openInput(usableGun, "白班", 1000));
        // 同一天换个班次再开，日期班次不撞，但这把枪的班还没交
        assertThatThrownBy(() -> service.create(openInput(usableGun, "夜班", 1500)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("一把枪同时只跑一个班");
    }

    @Test
    void open_rejectsDuplicateDateAndType() {
        service.create(openInput(usableGun, "白班", 1000));
        assertThatThrownBy(() -> service.create(openInput(repairGun, "白班", 2000)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已经开过了");
    }

    @Test
    void handover_computesVolumeAndLocksRecord() {
        ShiftRecord s = service.create(openInput(usableGun, "白班", 1000));

        ShiftRecord done = service.handover(s.id, 1600, 4500);
        assertThat(done.status).isEqualTo("已交接");
        assertThat(done.volume).isEqualTo(600);
        assertThat(done.endReading).isEqualTo(1600);

        // 已交接的班：再交不行，改也不行
        assertThatThrownBy(() -> service.handover(s.id, 1700, 100))
                .isInstanceOf(BizException.class);
        ShiftRecord edit = new ShiftRecord();
        edit.operator = "别人";
        assertThatThrownBy(() -> service.update(s.id, edit))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("改不了");

        ShiftRecord reloaded = shifts.findById(s.id).orElseThrow();
        assertThat(reloaded.gunId).isEqualTo(usableGun);
        assertThat(reloaded.startReading).isEqualTo(1000);
        assertThat(reloaded.endReading).isEqualTo(1600);
    }

    @Test
    void handover_failsWhenGunNotUsable_shiftStaysActive() {
        ShiftRecord s = service.create(openInput(usableGun, "白班", 1000));

        // 点交班前枪已成了维修（模拟另一边先落库的状态）
        FuelGun g = guns.findById(usableGun).orElseThrow();
        g.status = "维修";
        guns.save(g);

        assertThatThrownBy(() -> service.handover(s.id, 1600, 4500))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("交不出去");

        // 班还停在当班中，读数没被动过
        ShiftRecord reloaded = shifts.findById(s.id).orElseThrow();
        assertThat(reloaded.status).isEqualTo("当班中");
        assertThat(reloaded.endReading).isNull();
        assertThat(reloaded.volume).isNull();
    }

    @Test
    void gunUpdate_blockedWhileShiftActive() {
        service.create(openInput(usableGun, "白班", 1000));

        assertThatThrownBy(() -> gunService.update(usableGun, statusInput("维修")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("没交");
        assertThatThrownBy(() -> gunService.update(usableGun, statusInput("停用")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("没交");
        assertThat(guns.findById(usableGun).orElseThrow().status).isEqualTo("可用");
    }

    @Test
    void gunUpdate_allowedAfterHandover() {
        ShiftRecord s = service.create(openInput(usableGun, "白班", 1000));
        service.handover(s.id, 1600, 4500);

        FuelGun updated = gunService.update(usableGun, statusInput("维修"));
        assertThat(updated.status).isEqualTo("维修");
    }

    @Test
    void update_activeShiftCanFixReadingsButNotGun() {
        ShiftRecord s = service.create(openInput(usableGun, "白班", 1000));

        ShiftRecord edit = new ShiftRecord();
        edit.operator = "换人";
        edit.startReading = 1100;
        ShiftRecord updated = service.update(s.id, edit);
        assertThat(updated.operator).isEqualTo("换人");
        assertThat(updated.startReading).isEqualTo(1100);

        ShiftRecord changeGun = new ShiftRecord();
        changeGun.gunId = repairGun;
        assertThatThrownBy(() -> service.update(s.id, changeGun))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("改不了");
    }

    @Test
    void concurrent_handoverVsMaintenance_neverInconsistent() throws Exception {
        ShiftRecord s = service.create(openInput(usableGun, "白班", 1000));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger handoverOk = new AtomicInteger();
        AtomicInteger maintenanceOk = new AtomicInteger();

        pool.submit(() -> {
            ready.countDown();
            try {
                start.await();
                service.handover(s.id, 1600, 4500);
                handoverOk.incrementAndGet();
            } catch (BizException ignored) {
                // 枪被抢先改成维修/停用时，交班失败是允许的
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
            return null;
        });
        pool.submit(() -> {
            ready.countDown();
            try {
                start.await();
                gunService.update(usableGun, statusInput("维修"));
                maintenanceOk.incrementAndGet();
            } catch (BizException ignored) {
                // 班还没交时改维修被拦，是允许的
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
            return null;
        });

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        // 总有一边办成，不许两边都黄了
        assertThat(handoverOk.get() + maintenanceOk.get()).isGreaterThanOrEqualTo(1);

        ShiftRecord after = shifts.findById(s.id).orElseThrow();
        FuelGun gunAfter = guns.findById(usableGun).orElseThrow();
        // 维修能办成，只可能是班已经交掉了
        if (maintenanceOk.get() == 1) {
            assertThat(after.status).isEqualTo("已交接");
            assertThat(gunAfter.status).isEqualTo("维修");
        }
        if (handoverOk.get() == 1) {
            assertThat(after.status).isEqualTo("已交接");
            assertThat(after.volume).isEqualTo(600);
        }
        // 无论谁赢，都不许出现「班还当班中、枪却挂维修」的对不上的状态
        if ("当班中".equals(after.status)) {
            assertThat(gunAfter.status).isEqualTo("可用");
        }
    }

    @Test
    void concurrent_doubleHandover_onlyOneSucceeds() throws Exception {
        ShiftRecord s = service.create(openInput(usableGun, "白班", 1000));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        Runnable task = () -> {
            ready.countDown();
            try {
                start.await();
                service.handover(s.id, 1600, 4500);
                ok.incrementAndGet();
            } catch (BizException e) {
                failed.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        };
        pool.submit(task);
        pool.submit(task);

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(ok.get()).isEqualTo(1);
        assertThat(failed.get()).isEqualTo(1);
        ShiftRecord after = shifts.findById(s.id).orElseThrow();
        assertThat(after.status).isEqualTo("已交接");
        assertThat(after.volume).isEqualTo(600);
    }
}
