package com.gas.station.service;

import com.gas.station.dto.BizException;
import com.gas.station.entity.Inspection;
import com.gas.station.repository.InspectionRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionService {

    private final InspectionRepository inspections;

    public InspectionService(InspectionRepository inspections) {
        this.inspections = inspections;
    }

    public List<Inspection> list(String point, LocalDate inspectDate, String result, String status) {
        return inspections.findAllByOrderByIdDesc().stream()
                .filter(i -> point == null || point.isEmpty() || point.equals(i.point))
                .filter(i -> inspectDate == null || inspectDate.equals(i.inspectDate))
                .filter(i -> result == null || result.isEmpty() || result.equals(i.result))
                .filter(i -> status == null || status.isEmpty() || status.equals(i.status))
                .toList();
    }

    @Transactional
    public Inspection create(Inspection input) {
        if (input.inspectDate == null) {
            throw new BizException("请选择巡检日期");
        }
        if (input.point == null || input.point.isBlank()) {
            throw new BizException("要填巡检点位");
        }
        if (input.result == null || input.result.isBlank()) {
            throw new BizException("要选巡检结果是正常还是异常");
        }
        if ("异常".equals(input.result)
                && (input.issueDesc == null || input.issueDesc.isBlank())) {
            throw new BizException("报了异常就得写清是什么问题");
        }
        if (!inspections.findByPointAndInspectDate(input.point.trim(), input.inspectDate).isEmpty()) {
            throw new BizException(input.point + " 这天已经巡检过了，一个点位一天看一次");
        }
        Inspection saved = new Inspection();
        saved.inspectDate = input.inspectDate;
        saved.point = input.point.trim();
        saved.result = input.result;
        saved.issueDesc = input.issueDesc;
        saved.inspector = input.inspector;
        saved.status = "异常".equals(input.result) ? "待处理" : "已记录";
        return inspections.save(saved);
    }

    @Transactional
    public Inspection resolve(Long id) {
        Inspection inspection = inspections.findById(id)
                .orElseThrow(() -> new BizException("巡检记录不存在"));
        if (!"待处理".equals(inspection.status)) {
            throw new BizException("这条巡检记录不需要处理");
        }
        inspection.status = "已处理";
        return inspections.save(inspection);
    }
}
