package com.gas.station.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gas.station.dto.BizException;
import com.gas.station.entity.Inspection;
import com.gas.station.entity.Tank;
import com.gas.station.entity.Unloading;
import com.gas.station.repository.InspectionRepository;
import com.gas.station.repository.TankRepository;
import com.gas.station.repository.UnloadingRepository;
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
class UnloadingServiceTest {

    @Autowired
    private UnloadingService service;
    @Autowired
    private UnloadingRepository unloadings;
    @Autowired
    private TankRepository tanks;
    @Autowired
    private InspectionRepository inspections;

    private Long t1;
    private Long tRepair;

    @BeforeEach
    void setUp() {
        unloadings.deleteAll();
        inspections.deleteAll();
        tanks.deleteAll();

        Tank a = new Tank();
        a.code = "T-01";
        a.product = "92#";
        a.capacity = 10000;
        a.stock = 5000;
        a.safeStock = 1000;
        a.status = "在用";
        t1 = tanks.save(a).id;

        Tank r = new Tank();
        r.code = "T-99";
        r.product = "92#";
        r.capacity = 10000;
        r.stock = 0;
        r.safeStock = 1000;
        r.status = "检修";
        tRepair = tanks.save(r).id;
    }

    private Unloading input(String plate, String product, int volume, Long tankId) {
        Unloading u = new Unloading();
        u.plateNo = plate;
        u.product = product;
        u.plannedVolume = volume;
        u.tankId = tankId;
        u.operator = "测试员";
        return u;
    }

    private Inspection todayUnload(String status) {
        Inspection i = new Inspection();
        i.inspectDate = LocalDate.now(UnloadingService.ZONE);
        i.point = UnloadingService.UNLOAD_POINT;
        i.result = "异常";
        i.issueDesc = "渗漏";
        i.inspector = "安全员";
        i.status = status;
        return inspections.save(i);
    }

    @Test
    void create_passesChecksAndDoesNotTouchStock() {
        Unloading created = service.create(input("鲁B·00001", "92#", 4000, t1));
        assertThat(created.status).isEqualTo("待入罐");
        assertThat(created.billNo).startsWith("XY-");
        // 开单成功库存先不动
        assertThat(tanks.findById(t1).orElseThrow().stock).isEqualTo(5000);
    }

    @Test
    void create_rejectsWrongProduct() {
        assertThatThrownBy(() -> service.create(input("鲁B·00002", "95#", 1000, t1)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("油品不一致");
    }

    @Test
    void create_rejectsRepairTank() {
        assertThatThrownBy(() -> service.create(input("鲁B·00003", "92#", 1000, tRepair)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("检修");
    }

    @Test
    void create_rejectsOverCapacity() {
        // 此刻库存 5000，再卸 6000 就超罐容 10000
        assertThatThrownBy(() -> service.create(input("鲁B·00004", "92#", 6000, t1)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("超过罐容");
    }

    @Test
    void enter_blockedByPendingUnloadGate_andStockUntouched() {
        Unloading u = service.create(input("鲁B·00005", "92#", 1000, t1));
        todayUnload("待处理");

        assertThatThrownBy(() -> service.enter(u.id))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("卸油口");

        Unloading reloaded = unloadings.findById(u.id).orElseThrow();
        assertThat(reloaded.status).isEqualTo("待入罐");
        assertThat(reloaded.enteredAt).isNull();
        assertThat(tanks.findById(t1).orElseThrow().stock).isEqualTo(5000);
    }

    @Test
    void enter_writesStockOnlyAfterSuccess() {
        Unloading u = service.create(input("鲁B·00006", "92#", 3000, t1));
        todayUnload("已处理");

        Unloading entered = service.enter(u.id);
        assertThat(entered.status).isEqualTo("已入罐");
        assertThat(entered.enteredAt).isNotNull();
        assertThat(tanks.findById(t1).orElseThrow().stock).isEqualTo(8000);
    }

    @Test
    void enter_failsWhenTankSwitchedToRepair_stockAndBillUnchanged() {
        Unloading u = service.create(input("鲁B·00007", "92#", 1000, t1));
        todayUnload("已处理");

        Tank tank = tanks.findById(t1).orElseThrow();
        tank.status = "检修";
        tanks.save(tank);

        assertThatThrownBy(() -> service.enter(u.id))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("检修");
        assertThat(tanks.findById(t1).orElseThrow().stock).isEqualTo(5000);
        assertThat(unloadings.findById(u.id).orElseThrow().status).isEqualTo("待入罐");
    }

    @Test
    void enter_failsWhenStockAlreadyRaisedOverCapacity() {
        Unloading u = service.create(input("鲁B·00008", "92#", 4000, t1));
        todayUnload("已处理");

        // 库存刚被别人加过：5000 -> 7000，本单再 +4000 超罐容
        Tank tank = tanks.findById(t1).orElseThrow();
        tank.stock = 7000;
        tanks.save(tank);

        assertThatThrownBy(() -> service.enter(u.id))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("超过罐容");
        assertThat(tanks.findById(t1).orElseThrow().stock).isEqualTo(7000);
        assertThat(unloadings.findById(u.id).orElseThrow().status).isEqualTo("待入罐");
    }

    @Test
    void enteredBill_cannotBeEdited() {
        Unloading u = service.create(input("鲁B·00009", "92#", 1000, t1));
        todayUnload("已处理");
        service.enter(u.id);

        assertThatThrownBy(() -> service.update(u.id, input("鲁B·99999", "92#", 2000, t1)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能再改");
        Unloading reloaded = unloadings.findById(u.id).orElseThrow();
        assertThat(reloaded.plannedVolume).isEqualTo(1000);
        assertThat(reloaded.plateNo).isEqualTo("鲁B·00009");
        assertThat(tanks.findById(t1).orElseThrow().stock).isEqualTo(6000);
    }

    @Test
    void pendingBill_editRechecksTankAndVolume() {
        Unloading u = service.create(input("鲁B·00010", "92#", 1000, t1));
        // 改到超罐容也要拦住
        assertThatThrownBy(() -> service.update(u.id, input("鲁B·00010", "92#", 6000, t1)))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.update(u.id, input("鲁B·00010", "95#", 1000, t1)))
                .isInstanceOf(BizException.class);
        // 库存始终不动
        assertThat(tanks.findById(t1).orElseThrow().stock).isEqualTo(5000);
    }

    @Test
    void concurrentEnter_sameTankTwoBillsOnlyOneSucceeds() throws Exception {
        // 库存 5000 / 罐容 10000：两张各 4000 升，分开都能过，合起来 13000 超罐容
        Unloading a = service.create(input("鲁B·10001", "92#", 4000, t1));
        Unloading b = service.create(input("鲁B·10002", "92#", 4000, t1));
        todayUnload("已处理");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch done = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        pool.submit(runEnter(a.id, ready, start, done, success, failure));
        pool.submit(runEnter(b.id, ready, start, done, success, failure));
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(success.get()).isEqualTo(1);
        assertThat(failure.get()).isEqualTo(1);
        // 成功的那单加 4000，失败的没加，库存恰好 9000，绝不会超 10000
        assertThat(tanks.findById(t1).orElseThrow().stock).isEqualTo(9000);
        assertThat(unloadings.findAllByOrderByIdDesc().stream()
                .filter(u -> u.status.equals("已入罐")).count()).isEqualTo(1);
    }

    private java.util.concurrent.Callable<Void> runEnter(
            Long id, CountDownLatch ready, CountDownLatch start, CountDownLatch done,
            AtomicInteger success, AtomicInteger failure) {
        return () -> {
            ready.countDown();
            start.await();
            try {
                service.enter(id);
                success.incrementAndGet();
            } catch (BizException ex) {
                failure.incrementAndGet();
            } finally {
                done.countDown();
            }
            return null;
        };
    }
}
