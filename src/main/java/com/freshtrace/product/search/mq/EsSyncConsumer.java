package com.freshtrace.product.search.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshtrace.common.mq.AbstractRocketMqConsumer;
import com.freshtrace.common.mq.MqTags;
import com.freshtrace.common.mq.MqTopics;
import com.freshtrace.product.search.ProductSearchService;
import com.freshtrace.trade.entity.OrderItem;
import com.freshtrace.trade.entity.SubOrder;
import com.freshtrace.trade.mapper.OrderItemMapper;
import com.freshtrace.trade.mapper.SubOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ES 商品索引同步消费者：订阅 ES_SYNC 全部 tag。
 * <p>
 * - {@code product_upsert} / {@code product_delete}：商品创建/编辑/审核/状态变更触发的增量同步；
 * - {@code order_created} / {@code order_paid} / {@code order_cancelled}：订单事件后按订单商品重新索引，
 *   使销量等排序字段与 MySQL 最终一致（订单侧只发订单号，商品明细由本消费者回查）。
 * <p>
 * 幂等：索引写入按 productId upsert，重复投递结果一致；ES 异常向上抛出触发 MQ 重试。
 */
@Component
@ConditionalOnExpression("${rocketmq.enabled:true} and ${elasticsearch.enabled:true}")
@Slf4j
public class EsSyncConsumer extends AbstractRocketMqConsumer {

    private final ProductSearchService productSearchService;
    private final SubOrderMapper subOrderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ObjectMapper objectMapper;

    public EsSyncConsumer(ProductSearchService productSearchService,
                          SubOrderMapper subOrderMapper,
                          OrderItemMapper orderItemMapper,
                          ObjectMapper objectMapper,
                          @Value("${rocketmq.name-server:localhost:9876}") String nameServer,
                          @Value("${rocketmq.consumer.es-sync-group:freshtrace-es-sync-consumer}") String group) {
        super(nameServer, group, MqTopics.ES_SYNC, "*");
        this.productSearchService = productSearchService;
        this.subOrderMapper = subOrderMapper;
        this.orderItemMapper = orderItemMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handleMessage(MessageExt message) {
        JsonNode node;
        try {
            node = objectMapper.readTree(message.getBody());
        } catch (Exception e) {
            log.error("ES_SYNC message parse failed, msgId={}, tag={}", message.getMsgId(), message.getTags(), e);
            return;
        }
        String tag = message.getTags() == null ? "" : message.getTags();
        switch (tag) {
            case MqTags.PRODUCT_UPSERT -> indexProduct(node.path("productId").asLong(0L));
            case MqTags.PRODUCT_DELETE -> deleteProduct(node.path("productId").asLong(0L));
            case MqTags.ORDER_CREATED, MqTags.ORDER_PAID, MqTags.ORDER_CANCELLED ->
                    reindexByOrder(node.path("orderId").asLong(0L));
            default -> log.debug("ES_SYNC ignore tag={}, msgId={}", tag, message.getMsgId());
        }
    }

    private void indexProduct(long productId) {
        if (productId <= 0L) {
            log.warn("ES_SYNC missing productId");
            return;
        }
        productSearchService.indexProduct(productId);
    }

    private void deleteProduct(long productId) {
        if (productId <= 0L) {
            log.warn("ES_SYNC missing productId for delete");
            return;
        }
        productSearchService.deleteProduct(productId);
    }

    private void reindexByOrder(long orderId) {
        if (orderId <= 0L) {
            log.warn("ES_SYNC missing orderId");
            return;
        }
        List<SubOrder> subOrders = subOrderMapper.selectList(
                new LambdaQueryWrapper<SubOrder>().eq(SubOrder::getOrderId, orderId));
        List<Long> subOrderIds = subOrders.stream().map(SubOrder::getId).filter(Objects::nonNull).toList();
        if (subOrderIds.isEmpty()) {
            return;
        }
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().in(OrderItem::getSubOrderId, subOrderIds));
        Set<Long> productIds = items.stream()
                .map(OrderItem::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (Long productId : productIds) {
            productSearchService.indexProduct(productId);
        }
    }
}
