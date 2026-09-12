package com.freshtrace.community.controller;

import com.freshtrace.common.R;
import com.freshtrace.community.service.FarmerHomeService;
import com.freshtrace.community.vo.FarmerHomeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 果农主页接口（Phase 6 Day 4）。聚合查询带 Redis 缓存。
 */
@RestController
@RequestMapping("/farmer")
@RequiredArgsConstructor
public class FarmerHomeController {

    private final FarmerHomeService farmerHomeService;

    @GetMapping("/{id}/home")
    public R<FarmerHomeVO> home(@PathVariable Long id) {
        return R.ok(farmerHomeService.home(id));
    }
}
