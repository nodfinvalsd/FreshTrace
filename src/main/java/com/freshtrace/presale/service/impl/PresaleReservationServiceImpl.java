package com.freshtrace.presale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.common.PageVO;
import com.freshtrace.product.service.ProductService;
import com.freshtrace.product.vo.ProductVO;
import com.freshtrace.presale.dto.PresaleQueryDTO;
import com.freshtrace.presale.dto.ReserveDTO;
import com.freshtrace.presale.entity.Presale;
import com.freshtrace.presale.entity.PresaleReservation;
import com.freshtrace.presale.enums.PresaleStatus;
import com.freshtrace.presale.mapper.PresaleMapper;
import com.freshtrace.presale.mapper.PresaleReservationMapper;
import com.freshtrace.presale.service.PresaleReservationService;
import com.freshtrace.presale.support.PresaleCountSupport;
import com.freshtrace.presale.vo.ReservationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 预售预约实现（Phase 7 Day 2）。
 * <p>
 * 一致性设计（与 Phase 3 库存预扣同构）：
 * <ol>
 *     <li>Redis Lua 原子「限流 + INCR」快速拦截满额请求（冷启动用 DB 计数初始化）；</li>
 *     <li>MySQL 事务内条件自增 reservation_count（行锁权威闸门）+ INSERT 预约（UNIQUE 兜底重复）；</li>
 *     <li>任一失败回退 Redis 计数；Redis 与 MySQL 的偏差由 {@code PresaleCountReconciliationTask} 对账修复。</li>
 * </ol>
 */
@Service
@Slf4j
public class PresaleReservationServiceImpl implements PresaleReservationService {

    private final PresaleMapper presaleMapper;
    private final PresaleReservationMapper presaleReservationMapper;
    private final ProductService productService;
    private final PresaleCountSupport presaleCountSupport;
    private final TransactionTemplate transactionTemplate;

    public PresaleReservationServiceImpl(PresaleMapper presaleMapper,
                                         PresaleReservationMapper presaleReservationMapper,
                                         ProductService productService,
                                         PresaleCountSupport presaleCountSupport,
                                         PlatformTransactionManager transactionManager) {
        this.presaleMapper = presaleMapper;
        this.presaleReservationMapper = presaleReservationMapper;
        this.productService = productService;
        this.presaleCountSupport = presaleCountSupport;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public void reserve(Long userId, Long presaleId, ReserveDTO dto) {
        Presale presale = presaleMapper.selectById(presaleId);
        if (presale == null) {
            throw new BizException(ErrorCode.PRESALE_NOT_FOUND);
        }
        requireReservable(presale);

        int quantity = dto.getQuantity() == null ? 1 : dto.getQuantity();
        int maxReservations = presale.getMaxReservations() == null ? 0 : presale.getMaxReservations();
        int seedCount = presale.getReservationCount() == null ? 0 : presale.getReservationCount();
        if (presaleCountSupport.tryIncrement(presaleId, maxReservations, seedCount) < 0) {
            throw new BizException(ErrorCode.PRESALE_RESERVATION_FULL);
        }

        try {
            transactionTemplate.executeWithoutResult(status -> reserveTx(presaleId, userId, quantity));
        } catch (DataIntegrityViolationException e) {
            presaleCountSupport.compensate(presaleId);
            throw new BizException(ErrorCode.PRESALE_RESERVATION_EXISTS);
        } catch (RuntimeException e) {
            presaleCountSupport.compensate(presaleId);
            throw e;
        }
    }

    @Override
    public PageVO<ReservationVO> myReservations(Long userId, PresaleQueryDTO query) {
        Page<PresaleReservation> page = new Page<>(query.getPage(), query.getSize());
        presaleReservationMapper.selectPage(page, new LambdaQueryWrapper<PresaleReservation>()
                .eq(PresaleReservation::getUserId, userId)
                .orderByDesc(PresaleReservation::getId));
        if (page.getRecords().isEmpty()) {
            return PageVO.empty(query.getPage(), query.getSize());
        }

        List<Long> presaleIds = page.getRecords().stream()
                .map(PresaleReservation::getPresaleId).distinct().toList();
        Map<Long, Presale> presaleMap = presaleMapper.selectBatchIds(presaleIds).stream()
                .collect(Collectors.toMap(Presale::getId, Function.identity()));
        Map<Long, ProductVO> productMap = loadProducts(presaleMap.values().stream()
                .map(Presale::getProductId).distinct().toList());

        List<ReservationVO> records = page.getRecords().stream()
                .map(reservation -> toVO(reservation, presaleMap.get(reservation.getPresaleId()), productMap))
                .toList();
        return PageVO.of(page, records);
    }

    private void reserveTx(Long presaleId, Long userId, int quantity) {
        int rows = presaleMapper.incrementReservationCountIfAvailable(presaleId);
        if (rows == 0) {
            Presale latest = presaleMapper.selectById(presaleId);
            if (latest == null) {
                throw new BizException(ErrorCode.PRESALE_NOT_FOUND);
            }
            if (PresaleStatus.fromCode(latest.getStatus()) != PresaleStatus.ONGOING) {
                throw new BizException(ErrorCode.PRESALE_STATUS_INVALID, "预售已结束或已关闭");
            }
            throw new BizException(ErrorCode.PRESALE_RESERVATION_FULL);
        }

        PresaleReservation reservation = new PresaleReservation();
        reservation.setPresaleId(presaleId);
        reservation.setUserId(userId);
        reservation.setQuantity(quantity);
        reservation.setNotified(0);
        presaleReservationMapper.insert(reservation);
    }

    private void requireReservable(Presale presale) {
        if (PresaleStatus.fromCode(presale.getStatus()) != PresaleStatus.ONGOING) {
            throw new BizException(ErrorCode.PRESALE_STATUS_INVALID, "预售已结束或已关闭");
        }
        LocalDateTime now = LocalDateTime.now();
        if (presale.getPresaleStart() != null && now.isBefore(presale.getPresaleStart())) {
            throw new BizException(ErrorCode.PRESALE_STATUS_INVALID, "预售尚未开始");
        }
        if (presale.getPresaleEnd() != null && now.isAfter(presale.getPresaleEnd())) {
            throw new BizException(ErrorCode.PRESALE_STATUS_INVALID, "预售已截止");
        }
    }

    private Map<Long, ProductVO> loadProducts(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return productService.batchBrief(productIds).stream()
                .collect(Collectors.toMap(ProductVO::getId, Function.identity()));
    }

    private ReservationVO toVO(PresaleReservation reservation, Presale presale, Map<Long, ProductVO> productMap) {
        ReservationVO vo = new ReservationVO();
        vo.setId(reservation.getId());
        vo.setPresaleId(reservation.getPresaleId());
        vo.setQuantity(reservation.getQuantity());
        vo.setNotified(reservation.getNotified());
        vo.setCreateTime(reservation.getCreateTime());
        if (presale != null) {
            vo.setProductId(presale.getProductId());
            vo.setPresaleEnd(presale.getPresaleEnd());
            vo.setExpectedHarvest(presale.getExpectedHarvest());
            vo.setPresaleStatus(presale.getStatus());
            PresaleStatus status = PresaleStatus.fromCode(presale.getStatus());
            vo.setPresaleStatusDesc(status == null ? null : status.getDesc());
            ProductVO product = productMap.get(presale.getProductId());
            if (product != null) {
                vo.setProductTitle(product.getTitle());
                vo.setProductImage(product.getMainImage());
                vo.setProductPrice(product.getPrice());
            }
        }
        return vo;
    }
}
