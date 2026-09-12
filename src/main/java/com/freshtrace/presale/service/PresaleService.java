package com.freshtrace.presale.service;

import com.freshtrace.common.PageVO;
import com.freshtrace.presale.dto.PresaleCreateDTO;
import com.freshtrace.presale.dto.PresaleQueryDTO;
import com.freshtrace.presale.dto.PresaleUpdateDTO;
import com.freshtrace.presale.vo.PresaleVO;

/**
 * 预售配置管理。V1 预约提醒型预售：配置本身不产生价格与订单语义。
 * 果农侧管理预售配置；用户侧公开列表/详情与预约见 {@link PresaleReservationService}。
 */
public interface PresaleService {

    PresaleVO create(Long farmerId, PresaleCreateDTO dto);

    PresaleVO update(Long farmerId, Long id, PresaleUpdateDTO dto);

    void close(Long farmerId, Long id);

    /**
     * 进行中的预售列表（公开），按 presale_end 升序。
     */
    PageVO<PresaleVO> pageOnGoing(PresaleQueryDTO query);

    /**
     * 预售详情（公开）。
     */
    PresaleVO detail(Long id);
}
