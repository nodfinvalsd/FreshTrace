package com.freshtrace.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshtrace.trade.entity.ShoppingCart;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShoppingCartMapper extends BaseMapper<ShoppingCart> {

    /**
     * 加购：依赖 uk_user_product_spec 唯一索引处理并发。
     * <p>
     * - 无冲突：插入新行；
     * - 冲突且行有效（deleted=0）：数量原子累加；
     * - 冲突且行已逻辑删除（用户删过又重加）：数量重置为新值并恢复（deleted=0），
     *   避免把删除前的旧数量累加进来。
     */
    @Insert("INSERT INTO t_shopping_cart (id, user_id, product_id, spec_snapshot, quantity, selected, create_time, update_time, deleted) "
            + "VALUES (#{id}, #{userId}, #{productId}, #{specSnapshot}, #{quantity}, 1, NOW(), NOW(), 0) "
            + "ON DUPLICATE KEY UPDATE "
            + "quantity = IF(deleted = 1, VALUES(quantity), quantity + VALUES(quantity)), "
            + "selected = 1, update_time = NOW(), deleted = 0")
    int insertOrAccumulate(ShoppingCart cart);
}
