package com.freshtrace.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.user.dto.AddressDTO;
import com.freshtrace.user.entity.Address;
import com.freshtrace.user.mapper.AddressMapper;
import com.freshtrace.user.service.AddressService;
import com.freshtrace.user.vo.AddressVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final AddressMapper addressMapper;

    @Override
    @Transactional
    public AddressVO add(Long userId, AddressDTO dto) {
        Address address = new Address();
        address.setUserId(userId);
        copy(dto, address);
        if (address.getIsDefault() == null) {
            address.setIsDefault(0);
        }
        if (address.getIsDefault() == 1) {
            clearDefault(userId);
        }
        addressMapper.insert(address);
        return toVO(address);
    }

    @Override
    public List<AddressVO> list(Long userId) {
        List<Address> list = addressMapper.selectList(new LambdaQueryWrapper<Address>()
                .eq(Address::getUserId, userId)
                .orderByDesc(Address::getIsDefault)
                .orderByDesc(Address::getCreateTime));
        return list.stream().map(this::toVO).toList();
    }

    @Override
    public AddressVO detail(Long userId, Long addressId) {
        return toVO(getOwned(userId, addressId));
    }

    @Override
    @Transactional
    public AddressVO update(Long userId, Long addressId, AddressDTO dto) {
        Address address = getOwned(userId, addressId);
        copy(dto, address);
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            clearDefault(userId);
            address.setIsDefault(1);
        }
        addressMapper.updateById(address);
        return toVO(address);
    }

    @Override
    public void delete(Long userId, Long addressId) {
        Address address = getOwned(userId, addressId);
        addressMapper.deleteById(address.getId());
    }

    @Override
    @Transactional
    public AddressVO setDefault(Long userId, Long addressId) {
        Address address = getOwned(userId, addressId);
        clearDefault(userId);
        address.setIsDefault(1);
        addressMapper.updateById(address);
        return toVO(address);
    }

    private Address getOwned(Long userId, Long addressId) {
        Address address = addressMapper.selectById(addressId);
        if (address == null) {
            throw new BizException(ErrorCode.ADDRESS_NOT_FOUND);
        }
        if (!address.getUserId().equals(userId)) {
            throw new AccessDeniedException("无权限访问");
        }
        return address;
    }

    private void clearDefault(Long userId) {
        addressMapper.update(null, new LambdaUpdateWrapper<Address>()
                .eq(Address::getUserId, userId)
                .eq(Address::getIsDefault, 1)
                .set(Address::getIsDefault, 0));
    }

    private void copy(AddressDTO dto, Address address) {
        address.setReceiverName(dto.getReceiverName());
        address.setReceiverPhone(dto.getReceiverPhone());
        address.setProvince(dto.getProvince());
        address.setCity(dto.getCity());
        address.setDistrict(dto.getDistrict());
        address.setDetail(dto.getDetail());
        if (dto.getIsDefault() != null) {
            address.setIsDefault(dto.getIsDefault());
        }
    }

    private AddressVO toVO(Address address) {
        AddressVO vo = new AddressVO();
        vo.setId(address.getId());
        vo.setReceiverName(address.getReceiverName());
        vo.setReceiverPhone(address.getReceiverPhone());
        vo.setProvince(address.getProvince());
        vo.setCity(address.getCity());
        vo.setDistrict(address.getDistrict());
        vo.setDetail(address.getDetail());
        vo.setIsDefault(address.getIsDefault());
        return vo;
    }
}
