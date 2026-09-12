package com.freshtrace.presale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.common.PageVO;
import com.freshtrace.product.enums.ProductLifecycle;
import com.freshtrace.product.service.ProductService;
import com.freshtrace.product.vo.ProductDetailVO;
import com.freshtrace.product.vo.ProductVO;
import com.freshtrace.presale.dto.PresaleCreateDTO;
import com.freshtrace.presale.dto.PresaleQueryDTO;
import com.freshtrace.presale.dto.PresaleUpdateDTO;
import com.freshtrace.presale.entity.Presale;
import com.freshtrace.presale.entity.PresaleReservation;
import com.freshtrace.presale.enums.PresaleStatus;
import com.freshtrace.presale.mapper.PresaleMapper;
import com.freshtrace.presale.mapper.PresaleReservationMapper;
import com.freshtrace.presale.service.PresaleService;
import com.freshtrace.presale.support.PresaleMessageSupport;
import com.freshtrace.presale.vo.PresaleVO;
import lombok.extern.slf4j.Slf4j;
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
 * 预售配置实现（Phase 7 Day 1）。
 * <p>
 * 设计约定：
 * - 预售与商品 1:1，商品归属/审核校验通过后经 {@link ProductService#markPresale} 推进到「预售中」；
 * - 关闭预售 = 状态条件更新 ONGOING→CLOSED + 商品回退 PLANTING（{@link ProductService#markPresaleCancelled}）；
 * - 到期提醒采用 RocketMQ 延迟消息（delay = presale_end - now），Mysql 事务提交后再发送；
 *   发送失败不影响已提交配置，由 Phase 7 Day 3 的定时兜底扫描补偿。
 */
@Service
@Slf4j
public class PresaleServiceImpl implements PresaleService {

    private final PresaleMapper presaleMapper;
    private final PresaleReservationMapper presaleReservationMapper;
    private final ProductService productService;
    private final PresaleMessageSupport presaleMessageSupport;
    private final TransactionTemplate transactionTemplate;

    public PresaleServiceImpl(PresaleMapper presaleMapper,
                              PresaleReservationMapper presaleReservationMapper,
                              ProductService productService,
                              PresaleMessageSupport presaleMessageSupport,
                              PlatformTransactionManager transactionManager) {
        this.presaleMapper = presaleMapper;
        this.presaleReservationMapper = presaleReservationMapper;
        this.productService = productService;
        this.presaleMessageSupport = presaleMessageSupport;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public PresaleVO create(Long farmerId, PresaleCreateDTO dto) {
        ProductDetailVO product = requirePresaleableProduct(farmerId, dto.getProductId());
        validateTime(dto.getPresaleStart(), dto.getPresaleEnd());
        if (presaleMapper.selectOne(new LambdaQueryWrapper<Presale>()
                .eq(Presale::getProductId, dto.getProductId())) != null) {
            throw new BizException(ErrorCode.PRESALE_ALREADY_EXISTS);
        }

        PresaleVO vo = transactionTemplate.execute(status -> createTx(farmerId, dto, product));
        presaleMessageSupport.sendExpire(vo.getId(), vo.getProductId(), vo.getPresaleEnd());
        return vo;
    }

    @Override
    public PresaleVO update(Long farmerId, Long id, PresaleUpdateDTO dto) {
        Presale presale = requireOwnedPresale(farmerId, id);
        if (PresaleStatus.fromCode(presale.getStatus()) != PresaleStatus.ONGOING) {
            throw new BizException(ErrorCode.PRESALE_STATUS_INVALID, "仅进行中的预售可以修改");
        }
        validateTime(dto.getPresaleStart(), dto.getPresaleEnd());

        int maxReservations = dto.getMaxReservations() == null ? 0 : dto.getMaxReservations();
        long reserved = presaleReservationMapper.selectCount(new LambdaQueryWrapper<PresaleReservation>()
                .eq(PresaleReservation::getPresaleId, presale.getId()));
        if (maxReservations > 0 && maxReservations < reserved) {
            throw new BizException(ErrorCode.PARAM_ERROR, "最大预约数不能小于当前预约数");
        }

        PresaleVO vo = transactionTemplate.execute(status -> updateTx(presale, dto, maxReservations));
        presaleMessageSupport.sendExpire(vo.getId(), vo.getProductId(), vo.getPresaleEnd());
        return vo;
    }

    @Override
    public void close(Long farmerId, Long id) {
        Presale presale = requireOwnedPresale(farmerId, id);
        transactionTemplate.executeWithoutResult(status -> closeTx(presale));
    }

    @Override
    public PageVO<PresaleVO> pageOnGoing(PresaleQueryDTO query) {
        Page<Presale> page = new Page<>(query.getPage(), query.getSize());
        presaleMapper.selectPage(page, new LambdaQueryWrapper<Presale>()
                .eq(Presale::getStatus, PresaleStatus.ONGOING.getCode())
                .gt(Presale::getPresaleEnd, LocalDateTime.now())
                .orderByAsc(Presale::getPresaleEnd)
                .orderByAsc(Presale::getId));
        if (page.getRecords().isEmpty()) {
            return PageVO.empty(query.getPage(), query.getSize());
        }

        Map<Long, ProductVO> productMap = loadProducts(page.getRecords().stream()
                .map(Presale::getProductId).distinct().toList());
        List<PresaleVO> records = page.getRecords().stream()
                .map(presale -> toVO(presale, productMap.get(presale.getProductId())))
                .toList();
        return PageVO.of(page, records);
    }

    @Override
    public PresaleVO detail(Long id) {
        Presale presale = presaleMapper.selectById(id);
        if (presale == null) {
            throw new BizException(ErrorCode.PRESALE_NOT_FOUND);
        }
        return toVO(presale, loadProducts(List.of(presale.getProductId())).get(presale.getProductId()));
    }

    private Map<Long, ProductVO> loadProducts(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return productService.batchBrief(productIds).stream()
                .collect(Collectors.toMap(ProductVO::getId, Function.identity()));
    }

    private PresaleVO createTx(Long farmerId, PresaleCreateDTO dto, ProductDetailVO product) {
        Presale presale = new Presale();
        presale.setProductId(dto.getProductId());
        presale.setFarmerId(farmerId);
        presale.setPresaleStart(dto.getPresaleStart());
        presale.setPresaleEnd(dto.getPresaleEnd());
        presale.setExpectedHarvest(dto.getExpectedHarvest());
        presale.setMaxReservations(dto.getMaxReservations() == null ? 0 : dto.getMaxReservations());
        presale.setReservationCount(0);
        presale.setStatus(PresaleStatus.ONGOING.getCode());
        presaleMapper.insert(presale);

        productService.markPresale(dto.getProductId());
        return toVO(presale, product);
    }

    private PresaleVO updateTx(Presale presale, PresaleUpdateDTO dto, int maxReservations) {
        int rows = presaleMapper.update(null, new LambdaUpdateWrapper<Presale>()
                .eq(Presale::getId, presale.getId())
                .eq(Presale::getStatus, PresaleStatus.ONGOING.getCode())
                .set(Presale::getPresaleStart, dto.getPresaleStart())
                .set(Presale::getPresaleEnd, dto.getPresaleEnd())
                .set(Presale::getExpectedHarvest, dto.getExpectedHarvest())
                .set(Presale::getMaxReservations, maxReservations));
        if (rows == 0) {
            throw new BizException(ErrorCode.PRESALE_STATUS_INVALID, "预售状态已变更，请刷新后重试");
        }
        presale.setPresaleStart(dto.getPresaleStart());
        presale.setPresaleEnd(dto.getPresaleEnd());
        presale.setExpectedHarvest(dto.getExpectedHarvest());
        presale.setMaxReservations(maxReservations);
        return toVO(presale, productService.detail(presale.getProductId()));
    }

    private void closeTx(Presale presale) {
        int rows = presaleMapper.update(null, new LambdaUpdateWrapper<Presale>()
                .eq(Presale::getId, presale.getId())
                .eq(Presale::getStatus, PresaleStatus.ONGOING.getCode())
                .set(Presale::getStatus, PresaleStatus.CLOSED.getCode()));
        if (rows == 0) {
            throw new BizException(ErrorCode.PRESALE_STATUS_INVALID, "预售已结束或已关闭");
        }
        productService.markPresaleCancelled(presale.getProductId());
    }

    private ProductDetailVO requirePresaleableProduct(Long farmerId, Long productId) {
        ProductDetailVO product = productService.detail(productId);
        if (!product.getFarmerId().equals(farmerId)) {
            throw new BizException(ErrorCode.PRODUCT_PERMISSION_DENIED);
        }
        if (product.getAuditStatus() == null || product.getAuditStatus() != 1) {
            throw new BizException(ErrorCode.PRESALE_PRODUCT_NOT_READY, "商品未通过审核，不能开启预售");
        }
        ProductLifecycle lifecycle = ProductLifecycle.fromCode(product.getLifecycle());
        if (lifecycle != ProductLifecycle.PLANTING && lifecycle != ProductLifecycle.PRESALE) {
            throw new BizException(ErrorCode.PRESALE_PRODUCT_NOT_READY, "商品当前状态不能开启预售");
        }
        return product;
    }

    private Presale requireOwnedPresale(Long farmerId, Long id) {
        Presale presale = presaleMapper.selectById(id);
        if (presale == null) {
            throw new BizException(ErrorCode.PRESALE_NOT_FOUND);
        }
        if (!presale.getFarmerId().equals(farmerId)) {
            throw new BizException(ErrorCode.PRESALE_PERMISSION_DENIED);
        }
        return presale;
    }

    private void validateTime(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start) || !end.isAfter(LocalDateTime.now())) {
            throw new BizException(ErrorCode.PRESALE_TIME_INVALID, "预售截止时间必须晚于开始时间且晚于当前时间");
        }
    }

    private PresaleVO toVO(Presale presale, ProductVO product) {
        PresaleVO vo = new PresaleVO();
        vo.setId(presale.getId());
        vo.setProductId(presale.getProductId());
        vo.setFarmerId(presale.getFarmerId());
        vo.setPresaleStart(presale.getPresaleStart());
        vo.setPresaleEnd(presale.getPresaleEnd());
        vo.setExpectedHarvest(presale.getExpectedHarvest());
        vo.setMaxReservations(presale.getMaxReservations());
        vo.setReservationCount(presale.getReservationCount());
        vo.setStatus(presale.getStatus());
        PresaleStatus status = PresaleStatus.fromCode(presale.getStatus());
        vo.setStatusDesc(status == null ? null : status.getDesc());
        if (product != null) {
            vo.setProductTitle(product.getTitle());
            vo.setProductImage(product.getMainImage());
            vo.setProductPrice(product.getPrice());
            vo.setProductLifecycle(product.getLifecycle());
        }
        return vo;
    }
}
