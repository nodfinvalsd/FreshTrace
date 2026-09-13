package com.freshtrace.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.freshtrace.admin.dto.OperationLogQueryDTO;
import com.freshtrace.admin.entity.OperationLog;
import com.freshtrace.admin.mapper.OperationLogMapper;
import com.freshtrace.admin.service.OperationLogService;
import com.freshtrace.admin.vo.OperationLogVO;
import com.freshtrace.common.PageVO;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;
    private final UserMapper userMapper;

    @Override
    public void record(OperationLog operationLog) {
        operationLogMapper.insert(operationLog);
    }

    @Override
    public PageVO<OperationLogVO> page(OperationLogQueryDTO query) {
        Page<OperationLog> page = new Page<>(query.getPage(), query.getSize());
        operationLogMapper.selectPage(page, new LambdaQueryWrapper<OperationLog>()
                .eq(query.getOperatorId() != null, OperationLog::getOperatorId, query.getOperatorId())
                .eq(StringUtils.hasText(query.getTargetType()), OperationLog::getTargetType, query.getTargetType())
                .eq(StringUtils.hasText(query.getAction()), OperationLog::getAction, query.getAction())
                .ge(query.getStartTime() != null, OperationLog::getCreateTime, query.getStartTime())
                .le(query.getEndTime() != null, OperationLog::getCreateTime, query.getEndTime())
                .orderByDesc(OperationLog::getId));
        if (page.getRecords().isEmpty()) {
            return PageVO.empty(query.getPage(), query.getSize());
        }
        return PageVO.of(page, assemble(page.getRecords()));
    }

    private List<OperationLogVO> assemble(List<OperationLog> logs) {
        // 批量装配操作人名称，避免 N+1
        List<Long> operatorIds = logs.stream()
                .map(OperationLog::getOperatorId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, User> userMap = operatorIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(operatorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return logs.stream().map(log -> toVO(log, userMap.get(log.getOperatorId()))).toList();
    }

    private OperationLogVO toVO(OperationLog log, User operator) {
        OperationLogVO vo = new OperationLogVO();
        vo.setId(log.getId());
        vo.setOperatorId(log.getOperatorId());
        vo.setOperatorName(operator == null ? null
                : (operator.getNickname() != null ? operator.getNickname() : operator.getUsername()));
        vo.setTargetType(log.getTargetType());
        vo.setTargetId(log.getTargetId());
        vo.setAction(log.getAction());
        vo.setDetail(log.getDetail());
        vo.setIpAddress(log.getIpAddress());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }
}
