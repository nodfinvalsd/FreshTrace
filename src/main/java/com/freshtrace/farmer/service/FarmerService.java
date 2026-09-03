package com.freshtrace.farmer.service;

import com.freshtrace.farmer.dto.FarmerApplyDTO;
import com.freshtrace.farmer.dto.FarmerAuditDTO;
import com.freshtrace.farmer.vo.FarmerStatusVO;

public interface FarmerService {

    void apply(Long userId, FarmerApplyDTO dto);

    FarmerStatusVO getStatus(Long userId);

    void audit(FarmerAuditDTO dto, Long adminId);
}
