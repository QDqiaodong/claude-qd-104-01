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

    private static final LocalDate DAY = LocalDate.of(2026, 9, 18);

    @Autowired
    private ShiftService shifts;
    @Autowired
    private FuelGunService gunService;
    @Autowired
    private ShiftRecordRepository shiftRepo;
    @Autowired
    private FuelGunRepository gunRepo;
    @Autowired
    private TankRepository tanks;

    private Long g1;      // 可用
    private Long g2;      // 可用
    private Long gRepair; // 维修
    private Long gOff;    // 停用

    @BeforeEach
    void setUp() {
        shiftRepo.deleteAll();
        gunRepo.deleteAll();
        tanks.deleteAll();

        Tank t = new Tank();
        t.code = "T-01";
        t.product = "92#";
        t.capacity = 30000;
        t.stock = 10000;
        t.safeStock = 5000;
        t.status = "在用";
        Long tankId = tanks.save(t).id;

        g1 = saveGun("G-01", "可用", tankId);
        g2 = saveGun("G-02", "可用", tankId);
        gRepair = saveGun("G-03", "维修", tankId);
        gOff = saveGun("G-04", "停用", tankId);
    }

    private Long saveGun(String code, String status, Long tankId) {
        FuelGun g = new FuelGun();
        g.code = code;
        g.machineNo = "1号机";
        g.product = "92#";
        g.tankId = tankId;
        g.status = status;
        return gunRepo.save(g).id;
    }

    private ShiftRecord input(Long gunId, String shiftType, int startReading) {
        ShiftRecord s = new ShiftRecord();
        s.shiftDate = DAY;
        s.shiftType = shiftType;
        s.gunId = gunId;
        s.startReading = startReading;
        s.operator = "测试员";
        return s;
    }

    /** 不走服务规则，直接把枪改成某个状态，模拟「别人在别的页面上动了这把枪」。 */
    private void forceGunStatus(Long gunId, String status) {
        FuelGun g = gunRepo.findById(gunId).orElseThrow();
        g.status = status;
        gunRepo.save(g);
    }

    @Test
    void create_bindsGunAndStartReading() {
        ShiftRecord s = shifts.create(input(g1, "白班", 120000));
        assertThat(s.status).isEqualTo("当班中");
        // 接班读数记在这把枪上，关页再开（重新从库里读）也对得上
        ShiftRecord reloaded = shiftRepo.findById(s.id).orElseThrow();
        assertThat(reloaded.gunId).isEqualTo(g1);
        assertThat(reloaded.startReading).isEqualTo(120000);
    }

    @Test
    void create_needsGunAndOperator() {
        assertThatThrownBy(() -> shifts.create(input(null, "白班", 1000)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("选定一把");
        ShiftRecord noOperator = input(g1, "白班", 1000);
        noOperator.operator = " ";
        assertThatThrownBy(() -> shifts.create(noOperator))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("当班人");
    }

    @Test
    void create_rejectsRepairOrDisabledGun() {
        assertThatThrownBy(() -> shifts.create(input(gRepair, "白班", 1000)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("维修");
        assertThatThrownBy(() -> shifts.create(input(gOff, "白班", 1000)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("停用");
        assertThat(shiftRepo.findAll()).isEmpty();
    }

    @Test
    void create_rejectsGunAlreadyOnDuty() {
        shifts.create(input(g1, "白班", 1000));
        assertThatThrownBy(() -> shifts.create(input(g1, "夜班", 1000)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("先交完");
    }

    @Test
    void create_rejectsSameDateTypeTwice_evenOnDifferentGuns() {
        shifts.create(input(g1, "白班", 1000));
        assertThatThrownBy(() -> shifts.create(input(g2, "白班", 2000)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("只开一次");
    }

    @Test
    void handover_computesVolumeAndLocksRecord() {
        ShiftRecord s = shifts.create(input(g1, "白班", 120000));
        ShiftRecord done = shifts.handover(s.id, 125600, 42000);
        assertThat(done.status).isEqualTo("已交接");
        assertThat(done.volume).isEqualTo(5600);

        ShiftRecord reloaded = shiftRepo.findById(s.id).orElseThrow();
        assertThat(reloaded.gunId).isEqualTo(g1);
        assertThat(reloaded.endReading).isEqualTo(125600);

        // 已交接：再交一次不行，改也不让改，枪和两头读数都锁死
        assertThatThrownBy(() -> shifts.handover(s.id, 126000, 1))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已经交接");
        ShiftRecord edit = new ShiftRecord();
        edit.operator = "换人";
        edit.startReading = 1;
        assertThatThrownBy(() -> shifts.update(s.id, edit))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("改不了");
        reloaded = shiftRepo.findById(s.id).orElseThrow();
        assertThat(reloaded.startReading).isEqualTo(120000);
        assertThat(reloaded.operator).isEqualTo("测试员");
    }

    @Test
    void handover_rejectsEndBelowStart() {
        ShiftRecord s = shifts.create(input(g1, "白班", 1000));
        assertThatThrownBy(() -> shifts.handover(s.id, 900, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能小于");
        assertThat(shiftRepo.findById(s.id).orElseThrow().status).isEqualTo("当班中");
    }

    @Test
    void handover_failsWhenGunTurnedRepair_shiftStaysOnDuty() {
        ShiftRecord s = shifts.create(input(g1, "白班", 1000));
        // 点交班的同时，这把枪被别人挂成了维修
        forceGunStatus(g1, "维修");

        assertThatThrownBy(() -> shifts.handover(s.id, 1600, 100))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("维修")
                .hasMessageContaining("当班中");

        // 班还停在当班中，没变成已交接，读数也没写上去
        ShiftRecord reloaded = shiftRepo.findById(s.id).orElseThrow();
        assertThat(reloaded.status).isEqualTo("当班中");
        assertThat(reloaded.endReading).isNull();
        assertThat(reloaded.volume).isNull();

        // 枪修好恢复可用，这个班还能按原读数交掉
        forceGunStatus(g1, "可用");
        ShiftRecord done = shifts.handover(s.id, 1600, 100);
        assertThat(done.status).isEqualTo("已交接");
        assertThat(done.volume).isEqualTo(600);
    }

    @Test
    void handover_failsWhenGunDisabled() {
        ShiftRecord s = shifts.create(input(g1, "白班", 1000));
        forceGunStatus(g1, "停用");

        assertThatThrownBy(() -> shifts.handover(s.id, 1600, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("停用");
        assertThat(shiftRepo.findById(s.id).orElseThrow().status).isEqualTo("当班中");
    }

    @Test
    void gunStatus_repairOrDisableBlockedWhileOnDuty() {
        shifts.create(input(g1, "白班", 1000));

        FuelGun toRepair = new FuelGun();
        toRepair.status = "维修";
        assertThatThrownBy(() -> gunService.update(g1, toRepair))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("先交班");
        assertThat(gunRepo.findById(g1).orElseThrow().status).isEqualTo("可用");

        FuelGun toOff = new FuelGun();
        toOff.status = "停用";
        assertThatThrownBy(() -> gunService.update(g1, toOff))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("先交班");
        assertThat(gunRepo.findById(g1).orElseThrow().status).isEqualTo("可用");
    }

    @Test
    void gunStatus_repairAllowedAfterHandover() {
        ShiftRecord s = shifts.create(input(g1, "白班", 1000));
        shifts.handover(s.id, 1600, 100);

        FuelGun toRepair = new FuelGun();
        toRepair.status = "维修";
        gunService.update(g1, toRepair);
        assertThat(gunRepo.findById(g1).orElseThrow().status).isEqualTo("维修");
    }

    @Test
    void gunStatus_repairAllowedOnOtherGunWhileShiftOpen() {
        shifts.create(input(g1, "白班", 1000));
        // 当班中挂的是 G-01，别的枪照样能转维修
        FuelGun toRepair = new FuelGun();
        toRepair.status = "维修";
        gunService.update(g2, toRepair);
        assertThat(gunRepo.findById(g2).orElseThrow().status).isEqualTo("维修");
    }

    @Test
    void nextShiftOnSameGunOpensAfterHandover_readingChainHolds() {
        ShiftRecord s1 = shifts.create(input(g1, "白班", 120000));
        shifts.handover(s1.id, 125600, 42000);

        ShiftRecord s2 = shifts.create(input(g1, "夜班", 125600));
        assertThat(s2.status).isEqualTo("当班中");
        // 下一班接班读数 = 上一班交出去的那把枪的交班读数，链条对得上
        assertThat(s2.gunId).isEqualTo(g1);
        assertThat(s2.startReading).isEqualTo(shiftRepo.findById(s1.id).orElseThrow().endReading);
    }

    @Test
    void concurrentHandover_sameShiftOnlyOneSucceeds() throws Exception {
        ShiftRecord s = shifts.create(input(g1, "白班", 1000));
        AtomicInteger ok1 = new AtomicInteger();
        AtomicInteger ok2 = new AtomicInteger();

        race(() -> shifts.handover(s.id, 1600, 100),
                () -> shifts.handover(s.id, 1600, 100),
                ok1, ok2);

        // 同一个班点了两下交班，只许成功一下
        assertThat(ok1.get() + ok2.get()).isEqualTo(1);
        ShiftRecord reloaded = shiftRepo.findById(s.id).orElseThrow();
        assertThat(reloaded.status).isEqualTo("已交接");
        assertThat(reloaded.volume).isEqualTo(600);
    }

    @Test
    void concurrentRepairAndHandover_neverEndsInconsistent() throws Exception {
        ShiftRecord s = shifts.create(input(g1, "白班", 1000));
        AtomicInteger handoverOk = new AtomicInteger();
        AtomicInteger repairOk = new AtomicInteger();

        FuelGun toRepair = new FuelGun();
        toRepair.status = "维修";
        // 同一把枪，一边点维修、一边点交班
        race(() -> shifts.handover(s.id, 1600, 100),
                () -> gunService.update(g1, toRepair),
                handoverOk, repairOk);

        ShiftRecord reloaded = shiftRepo.findById(s.id).orElseThrow();
        FuelGun gun = gunRepo.findById(g1).orElseThrow();
        // 交班一定成了：维修那下要么被「当班中」挡回去，要么排在交班之后
        assertThat(handoverOk.get()).isEqualTo(1);
        assertThat(reloaded.status).isEqualTo("已交接");
        assertThat(reloaded.volume).isEqualTo(600);
        // 绝不出现「枪是维修/停用，班还挂在当班中」的对不上的状态
        assertThat(reloaded.status.equals("当班中") && !"可用".equals(gun.status)).isFalse();
        if (repairOk.get() == 1) {
            // 维修成功只可能发生在交班落库之后
            assertThat(gun.status).isEqualTo("维修");
        } else {
            assertThat(gun.status).isEqualTo("可用");
        }
    }

    @Test
    void concurrentCreate_sameGunOnlyOneOpens() throws Exception {
        AtomicInteger ok1 = new AtomicInteger();
        AtomicInteger ok2 = new AtomicInteger();

        race(() -> shifts.create(input(g1, "白班", 1000)),
                () -> shifts.create(input(g1, "夜班", 1000)),
                ok1, ok2);

        // 同一把枪同时开两个班，只许开一个
        assertThat(ok1.get() + ok2.get()).isEqualTo(1);
        assertThat(shiftRepo.findAll()).hasSize(1);
    }

    @Test
    void concurrentCreate_sameDateTypeDifferentGunsOnlyOneOpens() throws Exception {
        AtomicInteger ok1 = new AtomicInteger();
        AtomicInteger ok2 = new AtomicInteger();

        // 两把枪同时开同一天的白班，唯一键兜底，只许开一个
        race(() -> shifts.create(input(g1, "白班", 1000)),
                () -> shifts.create(input(g2, "白班", 2000)),
                ok1, ok2);

        assertThat(ok1.get() + ok2.get()).isEqualTo(1);
        assertThat(shiftRepo.findAll()).hasSize(1);
    }

    /** 两个操作同时起跑，各自统计成功（没抛 BizException）次数。 */
    private void race(Runnable first, Runnable second,
                      AtomicInteger firstOk, AtomicInteger secondOk) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        java.util.List<Throwable> unexpected = new java.util.concurrent.CopyOnWriteArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(runSilent(first, ready, start, done, firstOk, unexpected));
        pool.submit(runSilent(second, ready, start, done, secondOk, unexpected));
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(unexpected).isEmpty();
    }

    private java.util.concurrent.Callable<Void> runSilent(
            Runnable op, CountDownLatch ready, CountDownLatch start, CountDownLatch done,
            AtomicInteger success, java.util.List<Throwable> unexpected) {
        return () -> {
            ready.countDown();
            start.await();
            try {
                op.run();
                success.incrementAndGet();
            } catch (BizException ignored) {
                // 业务规则拦下的失败，正是要统计的
            } catch (Throwable t) {
                unexpected.add(t);
            } finally {
                done.countDown();
            }
            return null;
        };
    }
}
