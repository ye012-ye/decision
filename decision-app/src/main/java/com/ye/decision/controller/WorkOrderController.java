package com.ye.decision.controller;

import com.ye.decision.common.Result;
import com.ye.decision.domain.dto.WorkOrderCloseReq;
import com.ye.decision.domain.dto.WorkOrderCreateReq;
import com.ye.decision.domain.dto.WorkOrderLogVO;
import com.ye.decision.domain.dto.WorkOrderStatusUpdateReq;
import com.ye.decision.domain.dto.WorkOrderVO;
import com.ye.decision.domain.enums.WorkOrderPriority;
import com.ye.decision.domain.enums.WorkOrderStatus;
import com.ye.decision.domain.enums.WorkOrderType;
import com.ye.decision.service.WorkOrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/work-orders")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    public WorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @GetMapping
    public Result<List<WorkOrderVO>> list(@RequestParam(required = false) String orderNo,
                                          @RequestParam(required = false) String customerId,
                                          @RequestParam(required = false) WorkOrderStatus status,
                                          @RequestParam(required = false) WorkOrderType type,
                                          @RequestParam(required = false) WorkOrderPriority priority) {
        return Result.ok(workOrderService.list(orderNo, customerId, status, type, priority)
            .stream()
            .map(WorkOrderVO::from)
            .toList());
    }

    @PostMapping
    public Result<WorkOrderVO> create(@Valid @RequestBody WorkOrderCreateReq req) {
        return Result.ok(WorkOrderVO.from(workOrderService.create(
            req.type(),
            req.priority(),
            req.title(),
            req.description(),
            req.customerId(),
            req.sessionId()
        )));
    }

    @GetMapping("/{orderNo}")
    public Result<WorkOrderVO> getByOrderNo(@PathVariable String orderNo) {
        var entity = workOrderService.queryByOrderNo(orderNo);
        if (entity == null) {
            throw new IllegalArgumentException("工单不存在: " + orderNo);
        }
        return Result.ok(WorkOrderVO.from(entity));
    }

    @PatchMapping("/{orderNo}/status")
    public Result<WorkOrderVO> updateStatus(@PathVariable String orderNo,
                                            @Valid @RequestBody WorkOrderStatusUpdateReq req) {
        workOrderService.updateStatus(orderNo, req.status(), req.note(), req.operator());
        return Result.ok(WorkOrderVO.from(workOrderService.queryByOrderNo(orderNo)));
    }

    @PostMapping("/{orderNo}/close")
    public Result<WorkOrderVO> close(@PathVariable String orderNo,
                                     @Valid @RequestBody WorkOrderCloseReq req) {
        workOrderService.close(orderNo, req.resolution(), req.operator());
        return Result.ok(WorkOrderVO.from(workOrderService.queryByOrderNo(orderNo)));
    }

    @GetMapping("/{orderNo}/logs")
    public Result<List<WorkOrderLogVO>> logs(@PathVariable String orderNo) {
        return Result.ok(workOrderService.getLogsByOrderNo(orderNo)
            .stream()
            .map(WorkOrderLogVO::from)
            .toList());
    }
}
