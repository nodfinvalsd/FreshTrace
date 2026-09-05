package com.freshtrace.trade.controller;

import com.freshtrace.common.R;
import com.freshtrace.security.UserContext;
import com.freshtrace.trade.dto.CartAddDTO;
import com.freshtrace.trade.dto.CartQuantityDTO;
import com.freshtrace.trade.dto.CartSelectedDTO;
import com.freshtrace.trade.service.ShoppingCartService;
import com.freshtrace.trade.vo.CartVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class ShoppingCartController {

    private final ShoppingCartService shoppingCartService;

    @PostMapping
    public R<CartVO> add(@Valid @RequestBody CartAddDTO dto) {
        return R.ok(shoppingCartService.add(currentUserId(), dto));
    }

    @GetMapping
    public R<List<CartVO>> list() {
        return R.ok(shoppingCartService.list(currentUserId()));
    }

    @PutMapping("/{cartId}")
    public R<CartVO> updateQuantity(@PathVariable Long cartId, @Valid @RequestBody CartQuantityDTO dto) {
        return R.ok(shoppingCartService.updateQuantity(currentUserId(), cartId, dto));
    }

    @PutMapping("/{cartId}/selected")
    public R<CartVO> updateSelected(@PathVariable Long cartId, @Valid @RequestBody CartSelectedDTO dto) {
        return R.ok(shoppingCartService.updateSelected(currentUserId(), cartId, dto));
    }

    @DeleteMapping("/{cartId}")
    public R<Void> delete(@PathVariable Long cartId) {
        shoppingCartService.delete(currentUserId(), cartId);
        return R.ok();
    }

    private Long currentUserId() {
        return UserContext.get().getUserId();
    }
}
