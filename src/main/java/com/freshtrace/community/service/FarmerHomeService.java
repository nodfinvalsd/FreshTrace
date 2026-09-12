package com.freshtrace.community.service;

import com.freshtrace.community.vo.FarmerHomeVO;

public interface FarmerHomeService {

    /**
     * 果农主页聚合查询，带 Redis 缓存（TTL 1h，读时回写）。
     */
    FarmerHomeVO home(Long farmerId);

    /**
     * 失效果农主页缓存。供发帖/删帖/商品上下架/评价等写操作调用。
     */
    void evict(Long farmerId);
}
