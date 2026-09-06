package com.freshtrace.trace.controller;

import com.freshtrace.common.R;
import com.freshtrace.security.FarmerContext;
import com.freshtrace.security.FarmerRequired;
import com.freshtrace.trace.dto.TraceNodeCreateDTO;
import com.freshtrace.trace.dto.TraceNodeUpdateDTO;
import com.freshtrace.trace.service.TraceNodeService;
import com.freshtrace.trace.service.TraceTemplateService;
import com.freshtrace.trace.vo.TraceNodeVO;
import com.freshtrace.trace.vo.TraceTemplateNodeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 溯源接口（Phase 5 Day 2/Day 3）。
 * <p>
 * - 新增/修改/删除：@FarmerRequired 切面校验后经 FarmerContext 注入 farmerId；
 * - 时间线查询与模板查询：公开接口，无需果农身份。
 * Controller 不包含任何业务逻辑。
 */
@RestController
@RequestMapping("/trace")
@RequiredArgsConstructor
public class TraceController {

    private final TraceNodeService traceNodeService;
    private final TraceTemplateService traceTemplateService;

    @PostMapping("/nodes")
    @FarmerRequired
    public R<TraceNodeVO> create(@Valid @RequestBody TraceNodeCreateDTO dto) {
        return R.ok(traceNodeService.createNode(FarmerContext.get(), dto));
    }

    @PutMapping("/nodes/{id}")
    @FarmerRequired
    public R<TraceNodeVO> update(@PathVariable Long id, @Valid @RequestBody TraceNodeUpdateDTO dto) {
        return R.ok(traceNodeService.updateNode(FarmerContext.get(), id, dto));
    }

    @DeleteMapping("/nodes/{id}")
    @FarmerRequired
    public R<Void> delete(@PathVariable Long id) {
        traceNodeService.deleteNode(FarmerContext.get(), id);
        return R.ok();
    }

    @GetMapping("/timeline/{productId}")
    public R<List<TraceNodeVO>> timeline(@PathVariable Long productId) {
        return R.ok(traceNodeService.getTimeline(productId));
    }

    @GetMapping("/templates/{categoryId}")
    public R<List<TraceTemplateNodeVO>> templates(@PathVariable Long categoryId) {
        return R.ok(traceTemplateService.getTemplate(categoryId));
    }
}
