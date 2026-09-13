package com.freshtrace.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.common.PageVO;
import com.freshtrace.report.dto.ReportHandleDTO;
import com.freshtrace.report.dto.ReportQueryDTO;
import com.freshtrace.report.dto.ReportSubmitDTO;
import com.freshtrace.report.entity.Report;
import com.freshtrace.report.enums.ReportStatus;
import com.freshtrace.report.enums.ReportTargetType;
import com.freshtrace.report.mapper.ReportMapper;
import com.freshtrace.report.service.ReportService;
import com.freshtrace.report.vo.ReportVO;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 举报实现（Phase 10）。
 * <p>
 * 处理采用条件更新抢占（WHERE status=PENDING），保证同一举报只被处理一次，
 * 重复处理返回业务异常而非覆盖已有结论。
 */
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportMapper reportMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void submit(Long userId, ReportSubmitDTO dto) {
        if (ReportTargetType.fromCode(dto.getTargetType()) == null) {
            throw new BizException(ErrorCode.REPORT_TARGET_TYPE_INVALID);
        }
        Report report = new Report();
        report.setUserId(userId);
        report.setTargetType(dto.getTargetType());
        report.setTargetId(dto.getTargetId());
        report.setReason(dto.getReason());
        report.setEvidenceImages(toJson(dto.getEvidenceImages()));
        report.setStatus(ReportStatus.PENDING.getCode());
        reportMapper.insert(report);
    }

    @Override
    public PageVO<ReportVO> pageForAdmin(ReportQueryDTO query) {
        if (query.getStatus() != null && ReportStatus.fromCode(query.getStatus()) == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "举报状态参数非法");
        }
        if (query.getTargetType() != null && ReportTargetType.fromCode(query.getTargetType()) == null) {
            throw new BizException(ErrorCode.REPORT_TARGET_TYPE_INVALID);
        }
        Page<Report> page = new Page<>(query.getPage(), query.getSize());
        // 待处理(0) 排前，其余按提交时间倒序
        reportMapper.selectPage(page, new LambdaQueryWrapper<Report>()
                .eq(query.getStatus() != null, Report::getStatus, query.getStatus())
                .eq(query.getTargetType() != null, Report::getTargetType, query.getTargetType())
                .orderByAsc(Report::getStatus)
                .orderByDesc(Report::getId));
        if (page.getRecords().isEmpty()) {
            return PageVO.empty(query.getPage(), query.getSize());
        }
        return PageVO.of(page, assemble(page.getRecords()));
    }

    @Override
    public ReportVO adminDetail(Long id) {
        return assemble(List.of(loadReport(id))).get(0);
    }

    @Override
    public void handle(Long adminId, Long id, ReportHandleDTO dto) {
        if (dto.getStatus() != ReportStatus.HANDLED.getCode()
                && dto.getStatus() != ReportStatus.REJECTED.getCode()) {
            throw new BizException(ErrorCode.REPORT_STATUS_INVALID);
        }
        Report report = loadReport(id);
        if (report.getStatus() == null || report.getStatus() != ReportStatus.PENDING.getCode()) {
            throw new BizException(ErrorCode.REPORT_ALREADY_HANDLED);
        }
        // 条件更新抢占：仅待处理可被处理，并发/重复处理时 affectedRows=0
        int rows = reportMapper.update(null, new LambdaUpdateWrapper<Report>()
                .eq(Report::getId, id)
                .eq(Report::getStatus, ReportStatus.PENDING.getCode())
                .set(Report::getStatus, dto.getStatus())
                .set(Report::getHandlerId, adminId)
                .set(Report::getHandleReason, dto.getHandleReason())
                .set(Report::getHandledAt, LocalDateTime.now().withNano(0)));
        if (rows != 1) {
            throw new BizException(ErrorCode.REPORT_ALREADY_HANDLED);
        }
    }

    private Report loadReport(Long id) {
        Report report = reportMapper.selectById(id);
        if (report == null) {
            throw new BizException(ErrorCode.REPORT_NOT_FOUND);
        }
        return report;
    }

    private List<ReportVO> assemble(List<Report> reports) {
        List<Long> userIds = reports.stream().map(Report::getUserId).distinct().toList();
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return reports.stream().map(report -> toVO(report, userMap.get(report.getUserId()))).toList();
    }

    private ReportVO toVO(Report report, User reporter) {
        ReportStatus status = ReportStatus.fromCode(report.getStatus());
        ReportTargetType targetType = ReportTargetType.fromCode(report.getTargetType());
        ReportVO vo = new ReportVO();
        vo.setId(report.getId());
        vo.setUserId(report.getUserId());
        vo.setReporterName(reporter == null ? null
                : (reporter.getNickname() != null ? reporter.getNickname() : reporter.getUsername()));
        vo.setTargetType(report.getTargetType());
        vo.setTargetTypeDesc(targetType == null ? null : targetType.getDesc());
        vo.setTargetId(report.getTargetId());
        vo.setReason(report.getReason());
        vo.setEvidenceImages(parseJsonList(report.getEvidenceImages()));
        vo.setStatus(report.getStatus());
        vo.setStatusDesc(status == null ? null : status.getDesc());
        vo.setHandlerId(report.getHandlerId());
        vo.setHandleReason(report.getHandleReason());
        vo.setHandledAt(report.getHandledAt());
        vo.setCreateTime(report.getCreateTime());
        return vo;
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            throw new BizException("凭证图片序列化失败");
        }
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
