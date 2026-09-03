package com.freshtrace.user.controller;

import com.freshtrace.common.R;
import com.freshtrace.security.UserContext;
import com.freshtrace.user.dto.AddressDTO;
import com.freshtrace.user.service.AddressService;
import com.freshtrace.user.vo.AddressVO;
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
@RequestMapping("/user/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @PostMapping
    public R<AddressVO> add(@Valid @RequestBody AddressDTO dto) {
        return R.ok(addressService.add(currentUserId(), dto));
    }

    @GetMapping("/list")
    public R<List<AddressVO>> list() {
        return R.ok(addressService.list(currentUserId()));
    }

    @GetMapping("/{id}")
    public R<AddressVO> detail(@PathVariable Long id) {
        return R.ok(addressService.detail(currentUserId(), id));
    }

    @PutMapping("/{id}")
    public R<AddressVO> update(@PathVariable Long id, @Valid @RequestBody AddressDTO dto) {
        return R.ok(addressService.update(currentUserId(), id, dto));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        addressService.delete(currentUserId(), id);
        return R.ok();
    }

    @PutMapping("/{id}/default")
    public R<AddressVO> setDefault(@PathVariable Long id) {
        return R.ok(addressService.setDefault(currentUserId(), id));
    }

    private Long currentUserId() {
        return UserContext.get().getUserId();
    }
}
