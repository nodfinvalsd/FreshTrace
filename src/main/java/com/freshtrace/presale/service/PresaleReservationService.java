package com.freshtrace.presale.service;

import com.freshtrace.common.PageVO;
import com.freshtrace.presale.dto.PresaleQueryDTO;
import com.freshtrace.presale.dto.ReserveDTO;
import com.freshtrace.presale.vo.ReservationVO;

/**
 * 预售预约（用户侧）。V1 免费预约，仅登记成熟提醒意向，不产生订单/支付/价格锁定。
 */
public interface PresaleReservationService {

    void reserve(Long userId, Long presaleId, ReserveDTO dto);

    PageVO<ReservationVO> myReservations(Long userId, PresaleQueryDTO query);
}
