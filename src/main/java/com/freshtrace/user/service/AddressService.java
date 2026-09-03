package com.freshtrace.user.service;

import com.freshtrace.user.dto.AddressDTO;
import com.freshtrace.user.vo.AddressVO;

import java.util.List;

public interface AddressService {

    AddressVO add(Long userId, AddressDTO dto);

    List<AddressVO> list(Long userId);

    AddressVO detail(Long userId, Long addressId);

    AddressVO update(Long userId, Long addressId, AddressDTO dto);

    void delete(Long userId, Long addressId);

    AddressVO setDefault(Long userId, Long addressId);
}
