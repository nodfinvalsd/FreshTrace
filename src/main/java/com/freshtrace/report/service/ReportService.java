package com.freshtrace.report.service;

import com.freshtrace.common.PageVO;
import com.freshtrace.report.dto.ReportHandleDTO;
import com.freshtrace.report.dto.ReportQueryDTO;
import com.freshtrace.report.dto.ReportSubmitDTO;
import com.freshtrace.report.vo.ReportVO;

/**
 * 举报服务（Phase 10）。
 */
public interface ReportService {

    /** 用户提交举报 */
    void submit(Long userId, ReportSubmitDTO dto);

    /** 管理端举报列表（分页，可按状态/对象类型筛选） */
    PageVO<ReportVO> pageForAdmin(ReportQueryDTO query);

    /** 管理端举报详情（含凭证图片） */
    ReportVO adminDetail(Long id);

    /** 管理员处理举报：1=有效,2=驳回 */
    void handle(Long adminId, Long id, ReportHandleDTO dto);
}
