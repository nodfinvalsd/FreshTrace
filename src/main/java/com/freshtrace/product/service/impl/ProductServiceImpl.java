package com.freshtrace.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.common.PageVO;
import com.freshtrace.common.cache.CacheKeys;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.product.dto.ProductAttributeDTO;
import com.freshtrace.product.dto.ProductAuditDTO;
import com.freshtrace.product.dto.ProductAuditQueryDTO;
import com.freshtrace.product.dto.ProductCreateDTO;
import com.freshtrace.product.dto.ProductImageDTO;
import com.freshtrace.product.dto.ProductLifecycleUpdateDTO;
import com.freshtrace.product.dto.ProductUpdateDTO;
import com.freshtrace.product.entity.Category;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.entity.ProductAttribute;
import com.freshtrace.product.entity.ProductImage;
import com.freshtrace.product.entity.Spu;
import com.freshtrace.product.enums.ProductLifecycle;
import com.freshtrace.product.mapper.CategoryMapper;
import com.freshtrace.product.mapper.ProductAttributeMapper;
import com.freshtrace.product.mapper.ProductImageMapper;
import com.freshtrace.product.mapper.ProductMapper;
import com.freshtrace.product.mapper.SpuMapper;
import com.freshtrace.product.search.ProductEsSyncPublisher;
import com.freshtrace.product.service.ProductService;
import com.freshtrace.product.vo.AdminProductVO;
import com.freshtrace.product.vo.CategoryVO;
import com.freshtrace.product.vo.FarmerVO;
import com.freshtrace.product.vo.ProductAttributeVO;
import com.freshtrace.product.vo.ProductDetailVO;
import com.freshtrace.product.vo.ProductImageVO;
import com.freshtrace.product.vo.ProductVO;
import com.freshtrace.product.vo.SpuVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final ProductAttributeMapper productAttributeMapper;
    private final ProductImageMapper productImageMapper;
    private final SpuMapper spuMapper;
    private final CategoryMapper categoryMapper;
    private final FarmerMapper farmerMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ProductEsSyncPublisher productEsSyncPublisher;
    private final ObjectMapper objectMapper;

    private static final Duration DETAIL_CACHE_TTL = Duration.ofHours(1);

    @Override
    @Transactional
    public ProductVO create(Long userId, ProductCreateDTO dto) {
        if (spuMapper.selectById(dto.getSpuId()) == null) {
            throw new BizException(ErrorCode.PRODUCT_SPU_NOT_FOUND);
        }
        Farmer farmer = requireFarmer(userId);

        Product product = new Product();
        product.setSpuId(dto.getSpuId());
        product.setFarmerId(farmer.getId());
        product.setTitle(dto.getTitle());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());
        product.setUnit(StringUtils.hasText(dto.getUnit()) ? dto.getUnit() : "斤");
        product.setMainImage(dto.getMainImage());
        product.setLifecycle(dto.getLifecycle() == null ? ProductLifecycle.PLANTING.getCode() : dto.getLifecycle());
        product.setAuditStatus(0);
        product.setSalesCount(0);
        product.setViewCount(0);
        productMapper.insert(product);

        saveAttributes(product.getId(), dto.getAttributes());
        saveImages(product.getId(), dto.getImages());
        evictFarmerHome(farmer.getId());
        productEsSyncPublisher.publishUpsert(product.getId());
        return toVO(product);
    }

    @Override
    public ProductDetailVO detail(Long id) {
        String cacheKey = CacheKeys.productDetail(id);
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StringUtils.hasText(cached)) {
                return objectMapper.readValue(cached, ProductDetailVO.class);
            }
        } catch (Exception e) {
            log.warn("read product detail cache failed, productId={}", id, e);
        }

        ProductDetailVO vo = loadDetail(id);
        try {
            stringRedisTemplate.opsForValue().set(cacheKey,
                    objectMapper.writeValueAsString(vo), DETAIL_CACHE_TTL);
        } catch (Exception e) {
            log.warn("write product detail cache failed, productId={}", id, e);
        }
        return vo;
    }

    private ProductDetailVO loadDetail(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        Spu spu = spuMapper.selectById(product.getSpuId());
        Category category = spu == null ? null : categoryMapper.selectById(spu.getCategoryId());
        Farmer farmer = farmerMapper.selectById(product.getFarmerId());
        List<ProductAttribute> attributes = productAttributeMapper.selectList(
                new LambdaQueryWrapper<ProductAttribute>()
                        .eq(ProductAttribute::getProductId, id)
                        .orderByAsc(ProductAttribute::getId));
        List<ProductImage> images = productImageMapper.selectList(
                new LambdaQueryWrapper<ProductImage>()
                        .eq(ProductImage::getProductId, id)
                        .orderByAsc(ProductImage::getSortOrder)
                        .orderByAsc(ProductImage::getId));

        ProductDetailVO vo = new ProductDetailVO();
        copyToVO(product, vo);
        vo.setSpu(toSpuVO(spu));
        vo.setCategory(toCategoryVO(category));
        vo.setFarmer(toFarmerVO(farmer));
        vo.setAttributes(attributes.stream().map(this::toAttributeVO).toList());
        vo.setImages(images.stream().map(this::toImageVO).toList());
        vo.setReviewCount(0);
        vo.setTraceNodeCount(0);
        return vo;
    }

    @Override
    public List<ProductVO> batchBrief(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return productMapper.selectBatchIds(productIds).stream().map(this::toVO).toList();
    }

    @Override
    @Transactional
    public ProductVO update(Long userId, Long id, ProductUpdateDTO dto) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        checkOwner(product, userId);
        if (spuMapper.selectById(dto.getSpuId()) == null) {
            throw new BizException(ErrorCode.PRODUCT_SPU_NOT_FOUND);
        }

        boolean sensitiveChanged = isSensitiveChanged(product, dto);

        product.setSpuId(dto.getSpuId());
        product.setTitle(dto.getTitle());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());
        product.setUnit(StringUtils.hasText(dto.getUnit()) ? dto.getUnit() : "斤");
        product.setMainImage(dto.getMainImage());
        if (dto.getLifecycle() != null) {
            product.setLifecycle(dto.getLifecycle());
        }
        applyAuditStatusOnEdit(product, sensitiveChanged);
        product.setVersion(dto.getVersion());

        int rows = productMapper.updateById(product);
        if (rows == 0) {
            throw new BizException(ErrorCode.PRODUCT_VERSION_CONFLICT);
        }

        if (dto.getAttributes() != null) {
            saveAttributes(id, dto.getAttributes());
        }
        if (dto.getImages() != null) {
            saveImages(id, dto.getImages());
        }
        evictFarmerHome(product.getFarmerId());
        evictProductDetail(id);
        productEsSyncPublisher.publishUpsert(id);
        return toVO(product);
    }

    @Override
    public void audit(Long id, ProductAuditDTO dto) {
        if (dto.getAuditStatus() != 1 && dto.getAuditStatus() != 2) {
            throw new BizException(ErrorCode.PRODUCT_STATUS_INVALID);
        }
        if (dto.getAuditStatus() == 2 && !StringUtils.hasText(dto.getAuditReason())) {
            throw new BizException(ErrorCode.PRODUCT_AUDIT_REASON_REQUIRED);
        }
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        if (product.getAuditStatus() == null || product.getAuditStatus() != 0) {
            throw new BizException(ErrorCode.PRODUCT_AUDIT_NOT_PENDING);
        }
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, id)
                .set(Product::getAuditStatus, dto.getAuditStatus())
                .set(Product::getAuditReason, dto.getAuditStatus() == 1 ? null : dto.getAuditReason()));
        evictFarmerHome(product.getFarmerId());
        evictProductDetail(id);
        productEsSyncPublisher.publishUpsert(id);
    }

    @Override
    public PageVO<AdminProductVO> pageForAdmin(ProductAuditQueryDTO query) {
        Page<Product> page = new Page<>(query.getPage(), query.getSize());
        // 待审核(0) 排前，其余按提交时间倒序，便于管理员优先处理
        productMapper.selectPage(page, new LambdaQueryWrapper<Product>()
                .eq(query.getAuditStatus() != null, Product::getAuditStatus, query.getAuditStatus())
                .orderByAsc(Product::getAuditStatus)
                .orderByDesc(Product::getCreateTime));
        List<Product> products = page.getRecords();
        if (products.isEmpty()) {
            return PageVO.empty(query.getPage(), query.getSize());
        }
        // 批量装配 SPU / 品类 / 果农，避免 N+1
        List<Long> spuIds = products.stream().map(Product::getSpuId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Spu> spuMap = spuIds.isEmpty() ? Map.of()
                : spuMapper.selectBatchIds(spuIds).stream().collect(Collectors.toMap(Spu::getId, Function.identity()));
        List<Long> categoryIds = spuMap.values().stream().map(Spu::getCategoryId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Category> categoryMap = categoryIds.isEmpty() ? Map.of()
                : categoryMapper.selectBatchIds(categoryIds).stream().collect(Collectors.toMap(Category::getId, Function.identity()));
        List<Long> farmerIds = products.stream().map(Product::getFarmerId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Farmer> farmerMap = farmerIds.isEmpty() ? Map.of()
                : farmerMapper.selectBatchIds(farmerIds).stream().collect(Collectors.toMap(Farmer::getId, Function.identity()));

        List<AdminProductVO> records = products.stream().map(product -> {
            AdminProductVO vo = new AdminProductVO();
            copyToVO(product, vo);
            Spu spu = spuMap.get(product.getSpuId());
            Category category = spu == null ? null : categoryMap.get(spu.getCategoryId());
            Farmer farmer = farmerMap.get(product.getFarmerId());
            vo.setSpuName(spu == null ? null : spu.getName());
            vo.setCategoryName(category == null ? null : category.getName());
            vo.setFarmerName(farmer == null ? null : farmer.getRealName());
            vo.setOrchardName(farmer == null ? null : farmer.getOrchardName());
            return vo;
        }).toList();
        return PageVO.of(page, records);
    }

    @Override
    public ProductVO updateLifecycle(Long userId, Long id, ProductLifecycleUpdateDTO dto) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        checkOwner(product, userId);

        ProductLifecycle current = ProductLifecycle.fromCode(product.getLifecycle());
        ProductLifecycle target = ProductLifecycle.fromCode(dto.getLifecycle());
        if (current == null || target == null || !current.canTransitionTo(target)) {
            throw new BizException(ErrorCode.PRODUCT_STATUS_INVALID);
        }
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, id)
                .set(Product::getLifecycle, target.getCode()));
        product.setLifecycle(target.getCode());
        evictFarmerHome(product.getFarmerId());
        evictProductDetail(id);
        productEsSyncPublisher.publishUpsert(id);
        return toVO(product);
    }

    @Override
    public ProductVO cancelPreSale(Long userId, Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        checkOwner(product, userId);

        if (ProductLifecycle.fromCode(product.getLifecycle()) != ProductLifecycle.PRESALE) {
            throw new BizException(ErrorCode.PRODUCT_STATUS_INVALID);
        }
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, id)
                .set(Product::getLifecycle, ProductLifecycle.PLANTING.getCode()));
        product.setLifecycle(ProductLifecycle.PLANTING.getCode());
        evictFarmerHome(product.getFarmerId());
        evictProductDetail(id);
        productEsSyncPublisher.publishUpsert(id);
        return toVO(product);
    }

    @Override
    public ProductVO restock(Long userId, Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        checkOwner(product, userId);

        if (ProductLifecycle.fromCode(product.getLifecycle()) != ProductLifecycle.SOLD_OUT) {
            throw new BizException(ErrorCode.PRODUCT_STATUS_INVALID);
        }
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, id)
                .set(Product::getLifecycle, ProductLifecycle.ON_SALE.getCode()));
        product.setLifecycle(ProductLifecycle.ON_SALE.getCode());
        evictFarmerHome(product.getFarmerId());
        evictProductDetail(id);
        productEsSyncPublisher.publishUpsert(id);
        return toVO(product);
    }

    @Override
    @Transactional
    public void markPresale(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        ProductLifecycle current = ProductLifecycle.fromCode(product.getLifecycle());
        if (current == ProductLifecycle.PRESALE) {
            return;
        }
        if (current == null || !current.canTransitionTo(ProductLifecycle.PRESALE)) {
            throw new BizException(ErrorCode.PRODUCT_STATUS_INVALID, "商品当前状态不允许开启预售");
        }
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .set(Product::getLifecycle, ProductLifecycle.PRESALE.getCode()));
        evictFarmerHome(product.getFarmerId());
        evictProductDetail(productId);
        productEsSyncPublisher.publishUpsert(productId);
    }

    @Override
    @Transactional
    public void markPresaleCancelled(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        if (ProductLifecycle.fromCode(product.getLifecycle()) != ProductLifecycle.PRESALE) {
            return;
        }
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .set(Product::getLifecycle, ProductLifecycle.PLANTING.getCode()));
        evictFarmerHome(product.getFarmerId());
        evictProductDetail(productId);
        productEsSyncPublisher.publishUpsert(productId);
    }

    @Override
    @Transactional
    public void markPresaleEnded(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        ProductLifecycle current = ProductLifecycle.fromCode(product.getLifecycle());
        if (current == ProductLifecycle.ON_SALE) {
            return;
        }
        if (!canReachOnSale(current)) {
            throw new BizException(ErrorCode.PRODUCT_STATUS_INVALID, "商品当前状态不允许转为销售中");
        }
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .set(Product::getLifecycle, ProductLifecycle.ON_SALE.getCode()));
        evictFarmerHome(product.getFarmerId());
        evictProductDetail(productId);
        productEsSyncPublisher.publishUpsert(productId);
    }

    /**
     * 校验 from 是否存在满足状态机白名单的链路到达 ON_SALE（PRESALE → RIPE → ON_SALE，或 RIPE → ON_SALE）。
     */
    private boolean canReachOnSale(ProductLifecycle from) {
        ProductLifecycle cursor = from;
        for (int step = 0; step < ProductLifecycle.values().length && cursor != null; step++) {
            if (cursor == ProductLifecycle.ON_SALE) {
                return true;
            }
            ProductLifecycle next = switch (cursor) {
                case PRESALE -> ProductLifecycle.RIPE;
                case RIPE -> ProductLifecycle.ON_SALE;
                default -> null;
            };
            if (next == null || !cursor.canTransitionTo(next)) {
                return false;
            }
            cursor = next;
        }
        return false;
    }

    private void applyAuditStatusOnEdit(Product product, boolean sensitiveChanged) {
        Integer status = product.getAuditStatus();
        if (status == null) {
            return;
        }
        if (status == 1) {
            if (sensitiveChanged) {
                product.setAuditStatus(0);
                product.setAuditReason(null);
            }
            return;
        }
        if (status == 2) {
            product.setAuditStatus(0);
            product.setAuditReason(null);
        }
    }

    private boolean isSensitiveChanged(Product old, ProductUpdateDTO dto) {
        if (!Objects.equals(old.getTitle(), dto.getTitle())) {
            return true;
        }
        if (!Objects.equals(old.getSpuId(), dto.getSpuId())) {
            return true;
        }
        if (!Objects.equals(old.getMainImage(), dto.getMainImage())) {
            return true;
        }
        if (!Objects.equals(old.getDescription(), dto.getDescription())) {
            return true;
        }
        if (attributesChanged(old.getId(), dto.getAttributes())) {
            return true;
        }
        return imagesChanged(old.getId(), dto.getImages());
    }

    private boolean attributesChanged(Long productId, List<ProductAttributeDTO> newAttributes) {
        if (newAttributes == null) {
            return false;
        }
        List<ProductAttribute> oldList = productAttributeMapper.selectList(new LambdaQueryWrapper<ProductAttribute>()
                .eq(ProductAttribute::getProductId, productId));
        List<String> oldKeys = oldList.stream()
                .map(a -> a.getAttrName() + "|" + a.getAttrValue() + "|" + a.getExtraPrice())
                .sorted()
                .toList();
        List<String> newKeys = newAttributes.stream()
                .map(a -> a.getAttrName() + "|" + a.getAttrValue() + "|"
                        + (a.getExtraPrice() == null ? BigDecimal.ZERO : a.getExtraPrice()))
                .sorted()
                .toList();
        return !oldKeys.equals(newKeys);
    }

    private boolean imagesChanged(Long productId, List<ProductImageDTO> newImages) {
        if (newImages == null) {
            return false;
        }
        List<ProductImage> oldList = productImageMapper.selectList(new LambdaQueryWrapper<ProductImage>()
                .eq(ProductImage::getProductId, productId));
        List<String> oldUrls = oldList.stream()
                .map(ProductImage::getImageUrl)
                .sorted()
                .toList();
        List<String> newUrls = newImages.stream()
                .map(ProductImageDTO::getImageUrl)
                .sorted()
                .toList();
        return !oldUrls.equals(newUrls);
    }

    // ProductAttribute 与 ProductImage 是 Product 的从属聚合数据，没有独立业务生命周期，
    // 更新采用事务内「删除 + 插入」全量替换策略，因此使用物理删除，不使用 BaseEntity 的逻辑删除，
    // 以避免逻辑删除残留记录与 uk_product_attr 唯一索引冲突。
    private void saveAttributes(Long productId, List<ProductAttributeDTO> attributes) {
        productAttributeMapper.delete(new LambdaQueryWrapper<ProductAttribute>()
                .eq(ProductAttribute::getProductId, productId));
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        for (ProductAttributeDTO dto : attributes) {
            ProductAttribute attribute = new ProductAttribute();
            attribute.setProductId(productId);
            attribute.setAttrName(dto.getAttrName());
            attribute.setAttrValue(dto.getAttrValue());
            attribute.setExtraPrice(dto.getExtraPrice() == null ? BigDecimal.ZERO : dto.getExtraPrice());
            productAttributeMapper.insert(attribute);
        }
    }

    private void saveImages(Long productId, List<ProductImageDTO> images) {
        productImageMapper.delete(new LambdaQueryWrapper<ProductImage>()
                .eq(ProductImage::getProductId, productId));
        if (images == null || images.isEmpty()) {
            return;
        }
        for (ProductImageDTO dto : images) {
            ProductImage image = new ProductImage();
            image.setProductId(productId);
            image.setImageUrl(dto.getImageUrl());
            image.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
            productImageMapper.insert(image);
        }
    }

    /**
     * 失效果农主页缓存（商品上架/下架/审核/编辑改变主页在售商品展示）。
     */
    private void evictFarmerHome(Long farmerId) {
        try {
            stringRedisTemplate.delete(CacheKeys.farmerHome(farmerId));
        } catch (Exception e) {
            log.warn("invalidate farmer home cache failed, farmerId={}", farmerId, e);
        }
    }

    /**
     * 失效商品详情缓存（Phase 2.14）。
     */
    private void evictProductDetail(Long productId) {
        try {
            stringRedisTemplate.delete(CacheKeys.productDetail(productId));
        } catch (Exception e) {
            log.warn("invalidate product detail cache failed, productId={}", productId, e);
        }
    }

    private void checkOwner(Product product, Long userId) {
        Farmer farmer = requireFarmer(userId);
        if (!product.getFarmerId().equals(farmer.getId())) {
            throw new BizException(ErrorCode.PRODUCT_PERMISSION_DENIED);
        }
    }

    private Farmer requireFarmer(Long userId) {
        Farmer farmer = farmerMapper.selectOne(new LambdaQueryWrapper<Farmer>()
                .eq(Farmer::getUserId, userId));
        if (farmer == null) {
            throw new AccessDeniedException("需要果农认证");
        }
        return farmer;
    }

    private void copyToVO(Product product, ProductVO vo) {
        vo.setId(product.getId());
        vo.setSpuId(product.getSpuId());
        vo.setFarmerId(product.getFarmerId());
        vo.setTitle(product.getTitle());
        vo.setDescription(product.getDescription());
        vo.setPrice(product.getPrice());
        vo.setStock(product.getStock());
        vo.setUnit(product.getUnit());
        vo.setMainImage(product.getMainImage());
        vo.setLifecycle(product.getLifecycle());
        vo.setAuditStatus(product.getAuditStatus());
        vo.setAuditReason(product.getAuditReason());
        vo.setSalesCount(product.getSalesCount());
        vo.setViewCount(product.getViewCount());
        vo.setVersion(product.getVersion());
    }

    private ProductVO toVO(Product product) {
        ProductVO vo = new ProductVO();
        copyToVO(product, vo);
        return vo;
    }

    private SpuVO toSpuVO(Spu spu) {
        if (spu == null) {
            return null;
        }
        SpuVO vo = new SpuVO();
        vo.setId(spu.getId());
        vo.setCategoryId(spu.getCategoryId());
        vo.setName(spu.getName());
        vo.setVariety(spu.getVariety());
        vo.setOrigin(spu.getOrigin());
        vo.setDescription(spu.getDescription());
        vo.setMainImage(spu.getMainImage());
        vo.setStatus(spu.getStatus());
        return vo;
    }

    private CategoryVO toCategoryVO(Category category) {
        if (category == null) {
            return null;
        }
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setIconUrl(category.getIconUrl());
        vo.setSortOrder(category.getSortOrder());
        return vo;
    }

    private FarmerVO toFarmerVO(Farmer farmer) {
        if (farmer == null) {
            return null;
        }
        FarmerVO vo = new FarmerVO();
        vo.setId(farmer.getId());
        vo.setUserId(farmer.getUserId());
        vo.setRealName(farmer.getRealName());
        vo.setOrchardName(farmer.getOrchardName());
        vo.setOrchardProvince(farmer.getOrchardProvince());
        vo.setOrchardCity(farmer.getOrchardCity());
        vo.setOrchardDistrict(farmer.getOrchardDistrict());
        vo.setAvgRating(farmer.getAvgRating());
        vo.setTotalSales(farmer.getTotalSales());
        return vo;
    }

    private ProductAttributeVO toAttributeVO(ProductAttribute attribute) {
        ProductAttributeVO vo = new ProductAttributeVO();
        vo.setId(attribute.getId());
        vo.setAttrName(attribute.getAttrName());
        vo.setAttrValue(attribute.getAttrValue());
        vo.setExtraPrice(attribute.getExtraPrice());
        return vo;
    }

    private ProductImageVO toImageVO(ProductImage image) {
        ProductImageVO vo = new ProductImageVO();
        vo.setId(image.getId());
        vo.setImageUrl(image.getImageUrl());
        vo.setSortOrder(image.getSortOrder());
        return vo;
    }
}
