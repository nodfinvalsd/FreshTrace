package com.freshtrace.admin.service;

import com.freshtrace.admin.entity.OperationLog;
import com.freshtrace.admin.dto.OperationLogQueryDTO;
import com.freshtrace.admin.vo.OperationLogVO;
import com.freshtrace.common.PageVO;

/**
 * 操作日志服务。
 */
public interface OperationLogService {

    /**
     * 记录一条操作日志。由切面调用，写入失败不得影响主业务。
     */
    void record(OperationLog operationLog);

    /**
     * 管理端操作日志查询（分页，可按操作人/对象类型/动作/时间段筛选）。
     */
    PageVO<OperationLogVO> page(OperationLogQueryDTO query);
}
