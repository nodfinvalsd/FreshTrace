package com.freshtrace.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.entity.ProductAttribute;
import com.freshtrace.product.enums.ProductLifecycle;
import com.freshtrace.product.mapper.ProductAttributeMapper;
import com.freshtrace.product.mapper.ProductMapper;
import com.freshtrace.trade.dto.CartAddDTO;
import com.freshtrace.trade.dto.CartQuantityDTO;
import com.freshtrace.trade.dto.CartSelectedDTO;
import com.freshtrace.trade.entity.ShoppingCart;
import com.freshtrace.trade.mapper.ShoppingCartMapper;
import com.freshtrace.trade.service.ShoppingCartService;
import com.freshtrace.trade.support.SpecSupport;
import com.freshtrace.trade.vo.CartVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShoppingCartServiceImpl implements ShoppingCartService {

    private final ShoppingCartMapper shoppingCartMapper;
    private final ProductMapper productMapper;
    private final ProductAttributeMapper productAttributeMapper;
    private final SpecSupport specSupport;

    @Override
    public CartVO add(Long userId, CartAddDTO dto) {
        Product product = requireOnSaleProduct(dto.getProductId());
        Map<String, BigDecimal> attrPrices = loadAttrPrices(dto.getProductId());
        SpecSupport.ParsedSpec parsed = specSupport.parse(dto.getSpecSnapshot(), attrPrices);

        ShoppingCart cart = new ShoppingCart();
        cart.setId(IdWorker.getId());
        cart.setUserId(userId);
        cart.setProductId(dto.getProductId());
        cart.setSpecSnapshot(parsed.canonicalSnapshot());
        cart.setQuantity(dto.getQuantity());
        cart.setSelected(1);
        shoppingCartMapper.insertOrAccumulate(cart);

        ShoppingCart saved = shoppingCartMapper.selectOne(new LambdaQueryWrapper<ShoppingCart>()
                .eq(ShoppingCart::getUserId, userId)
                .eq(ShoppingCart::getProductId, dto.getProductId())
                .eq(ShoppingCart::getSpecSnapshot, parsed.canonicalSnapshot()));
        return toVO(saved, product, attrPrices);
    }

    @Override
    public CartVO updateQuantity(Long userId, Long cartId, CartQuantityDTO dto) {
        ShoppingCart cart = requireOwnCart(userId, cartId);
        cart.setQuantity(dto.getQuantity());
        shoppingCartMapper.updateById(cart);
        return toVO(cart);
    }

    @Override
    public CartVO updateSelected(Long userId, Long cartId, CartSelectedDTO dto) {
        ShoppingCart cart = requireOwnCart(userId, cartId);
        cart.setSelected(Boolean.TRUE.equals(dto.getSelected()) ? 1 : 0);
        shoppingCartMapper.updateById(cart);
        return toVO(cart);
    }

    @Override
    public void delete(Long userId, Long cartId) {
        requireOwnCart(userId, cartId);
        shoppingCartMapper.deleteById(cartId);
    }

    @Override
    public List<CartVO> list(Long userId) {
        List<ShoppingCart> carts = shoppingCartMapper.selectList(new LambdaQueryWrapper<ShoppingCart>()
                .eq(ShoppingCart::getUserId, userId)
                .orderByDesc(ShoppingCart::getCreateTime));
        if (carts.isEmpty()) {
            return List.of();
        }
        Set<Long> productIds = carts.stream().map(ShoppingCart::getProductId).collect(Collectors.toSet());
        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        Map<Long, Map<String, BigDecimal>> attrPriceMaps = loadAttrPrices(productIds);

        return carts.stream()
                .map(cart -> toVO(cart, productMap.get(cart.getProductId()),
                        attrPriceMaps.getOrDefault(cart.getProductId(), Map.of())))
                .toList();
    }

    private CartVO toVO(ShoppingCart cart) {
        Product product = productMapper.selectById(cart.getProductId());
        return toVO(cart, product, product == null ? Map.of() : loadAttrPrices(product.getId()));
    }

    private CartVO toVO(ShoppingCart cart, Product product, Map<String, BigDecimal> attrPrices) {
        CartVO vo = new CartVO();
        vo.setCartId(cart.getId());
        vo.setProductId(cart.getProductId());
        vo.setSpecSnapshot(cart.getSpecSnapshot());
        vo.setQuantity(cart.getQuantity());
        vo.setSelected(cart.getSelected() != null && cart.getSelected() == 1);
        if (product == null) {
            vo.setProductExists(false);
            vo.setOnSale(false);
            vo.setInsufficientStock(true);
            return vo;
        }
        vo.setProductExists(true);
        vo.setProductTitle(product.getTitle());
        vo.setProductImage(product.getMainImage());
        vo.setPrice(product.getPrice());
        vo.setOnSale(isOnSale(product));
        vo.setStock(product.getStock());

        BigDecimal extraPrice = BigDecimal.ZERO;
        if (StringUtils.hasText(cart.getSpecSnapshot())) {
            try {
                extraPrice = specSupport.parse(cart.getSpecSnapshot(), attrPrices).extraPrice();
            } catch (BizException e) {
                // 商品规格在加购后被果农调整，导致旧快照失效：展示时退回基础价，下单时会重新强校验
                extraPrice = BigDecimal.ZERO;
            }
        }
        BigDecimal unitPrice = product.getPrice().add(extraPrice);
        vo.setUnitPrice(unitPrice);
        vo.setTotalPrice(unitPrice.multiply(BigDecimal.valueOf(cart.getQuantity())));
        vo.setInsufficientStock(product.getStock() != null && cart.getQuantity() > product.getStock());
        return vo;
    }

    private boolean isOnSale(Product product) {
        return product.getLifecycle() != null
                && product.getLifecycle() == ProductLifecycle.ON_SALE.getCode()
                && product.getAuditStatus() != null
                && product.getAuditStatus() == 1;
    }

    private Product requireOnSaleProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        if (!isOnSale(product)) {
            throw new BizException(ErrorCode.PRODUCT_NOT_ON_SALE);
        }
        return product;
    }

    private ShoppingCart requireOwnCart(Long userId, Long cartId) {
        ShoppingCart cart = shoppingCartMapper.selectById(cartId);
        if (cart == null) {
            throw new BizException(ErrorCode.CART_ITEM_NOT_FOUND);
        }
        if (!cart.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.CART_ITEM_PERMISSION_DENIED);
        }
        return cart;
    }

    private Map<String, BigDecimal> loadAttrPrices(Long productId) {
        return loadAttrPrices(Set.of(productId)).getOrDefault(productId, Map.of());
    }

    private Map<Long, Map<String, BigDecimal>> loadAttrPrices(Set<Long> productIds) {
        Map<Long, Map<String, BigDecimal>> result = new HashMap<>();
        if (productIds.isEmpty()) {
            return result;
        }
        List<ProductAttribute> attributes = productAttributeMapper.selectList(
                new LambdaQueryWrapper<ProductAttribute>().in(ProductAttribute::getProductId, productIds));
        for (ProductAttribute attribute : attributes) {
            result.computeIfAbsent(attribute.getProductId(), k -> new HashMap<>())
                    .put(attribute.getAttrName() + "|" + attribute.getAttrValue(),
                            attribute.getExtraPrice() == null ? BigDecimal.ZERO : attribute.getExtraPrice());
        }
        return result;
    }
}
