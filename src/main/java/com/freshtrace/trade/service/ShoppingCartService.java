package com.freshtrace.trade.service;

import com.freshtrace.trade.dto.CartAddDTO;
import com.freshtrace.trade.dto.CartQuantityDTO;
import com.freshtrace.trade.dto.CartSelectedDTO;
import com.freshtrace.trade.vo.CartVO;

import java.util.List;

public interface ShoppingCartService {

    CartVO add(Long userId, CartAddDTO dto);

    CartVO updateQuantity(Long userId, Long cartId, CartQuantityDTO dto);

    CartVO updateSelected(Long userId, Long cartId, CartSelectedDTO dto);

    void delete(Long userId, Long cartId);

    List<CartVO> list(Long userId);
}
