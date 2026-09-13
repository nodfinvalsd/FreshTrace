package com.freshtrace.farmer.service;

import com.freshtrace.common.PageVO;
import com.freshtrace.farmer.dto.FarmerApplyDTO;
import com.freshtrace.farmer.dto.FarmerAuditDTO;
import com.freshtrace.farmer.dto.FarmerAuditQueryDTO;
import com.freshtrace.farmer.vo.AdminFarmerVO;
import com.freshtrace.farmer.vo.FarmerStatusVO;

public interface FarmerService {

    void apply(Long userId, FarmerApplyDTO dto);

    FarmerStatusVO getStatus(Long userId);

    void audit(FarmerAuditDTO dto, Long adminId);

    /**
     * 管理端果农认证审核列表（分页，可按认证状态筛选）。
     */
    PageVO<AdminFarmerVO> pageForAdmin(FarmerAuditQueryDTO query);

    /**
     * 管理端果农认证详情（解密身份证号供审核核验）。
     */
    AdminFarmerVO adminDetail(Long id);
}
