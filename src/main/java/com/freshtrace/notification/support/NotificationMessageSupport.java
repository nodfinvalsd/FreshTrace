package com.freshtrace.notification.support;

import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.notification.entity.Notification;
import com.freshtrace.notification.enums.NotificationEvent;
import com.freshtrace.notification.enums.NotificationType;
import com.freshtrace.notification.service.NotificationService;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 通知事件装配（Phase 9）。
 * <p>
 * 职责：把各业务模块发来的 MQ 消息（JSON）翻译成站内通知并交给 {@link NotificationService} 落库。
 * 消费者只负责解析与分发，真正的「事件 → 接收人 / 标题 / 正文 / 幂等键」映射集中在此，
 * 保证同一事件在多消费者/多入口下语义一致。
 * <p>
 * 接收人解析：果农事件的 farmerId 是 t_farmer.id，需换算为登录账号 t_user.id；
 * 买家事件的消息体已携带 user id，直接使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationMessageSupport {

    private final NotificationService notificationService;
    private final FarmerMapper farmerMapper;
    private final ProductMapper productMapper;

    /**
     * 处理果农侧通知事件（FARMER_NOTIFICATION：下单/支付/取消）。
     */
    public void handleFarmerEvent(String tag, JsonNode node) {
        NotificationEvent event = NotificationEvent.fromTag(tag);
        if (event == null || event.getType() != NotificationType.ORDER) {
            log.warn("unexpected farmer notification event, tag={}", tag);
            return;
        }
        Long farmerId = asLong(node, "farmerId");
        Long subOrderId = asLong(node, "subOrderId");
        String subOrderNo = node.path("subOrderNo").asText("");
        if (farmerId == null) {
            log.warn("farmer notification missing farmerId, tag={}", tag);
            return;
        }
        Long receiverId = findFarmerUserId(farmerId);
        if (receiverId == null) {
            log.warn("farmer not found, skip notification, farmerId={}, tag={}", farmerId, tag);
            return;
        }
        String content = switch (event) {
            case ORDER_CREATED -> "您有一笔新订单（" + subOrderNo + "），请及时处理";
            case ORDER_PAID -> "订单（" + subOrderNo + "）买家已支付，请尽快发货";
            case ORDER_CANCELLED -> "订单（" + subOrderNo + "）已取消或退款，请留意";
            default -> null;
        };
        if (content == null) {
            log.warn("farmer notification content not defined, tag={}", tag);
            return;
        }
        notificationService.push(receiverId, event.getType(), event.getTitle(), content,
                subOrderId, dedupKey(event, subOrderId, receiverId));
    }

    /**
     * 处理买家侧业务通知事件（NOTIFICATION：发货/确认收货/评价回复/售后退款）。
     */
    public void handleBusinessEvent(String tag, JsonNode node) {
        NotificationEvent event = NotificationEvent.fromTag(tag);
        if (event == null) {
            log.warn("unknown notification event, tag={}", tag);
            return;
        }
        switch (event) {
            case ORDER_SHIPPED -> handleShipped(node);
            case ORDER_RECEIVED -> handleReceived(node);
            case REVIEW_REPLIED -> handleReviewReplied(node);
            case REFUND_APPLIED -> handleRefundApplied(node);
            case REFUND_FARMER_APPROVED -> handleRefundToBuyer(node, NotificationEvent.REFUND_FARMER_APPROVED,
                    "果农已同意退款，退款将原路返回，请留意到账");
            case REFUND_FARMER_REJECTED -> handleRefundToBuyer(node, NotificationEvent.REFUND_FARMER_REJECTED,
                    "果农拒绝了您的退款申请，如有异议可申请平台仲裁");
            case REFUND_ARBITRATION_APPROVED -> handleRefundToBuyer(node, NotificationEvent.REFUND_ARBITRATION_APPROVED,
                    "平台仲裁已判定退款成立，退款将原路返回");
            case REFUND_ARBITRATION_REJECTED -> handleRefundToBuyer(node, NotificationEvent.REFUND_ARBITRATION_REJECTED,
                    "平台仲裁驳回了您的退款申请");
            default -> log.warn("business notification not handled, tag={}", tag);
        }
    }

    /** 买家申请售后 → 通知果农处理 */
    private void handleRefundApplied(JsonNode node) {
        Long farmerId = asLong(node, "farmerId");
        Long subOrderId = asLong(node, "subOrderId");
        String subOrderNo = node.path("subOrderNo").asText("");
        if (farmerId == null) {
            log.warn("refund applied notification missing farmerId, subOrderNo={}", subOrderNo);
            return;
        }
        Long receiverId = findFarmerUserId(farmerId);
        if (receiverId == null) {
            log.warn("farmer not found, skip refund applied notification, farmerId={}", farmerId);
            return;
        }
        String content = "买家对子订单（" + subOrderNo + "）申请退款，请及时处理";
        notificationService.push(receiverId, NotificationType.ORDER,
                NotificationEvent.REFUND_APPLIED.getTitle(), content, subOrderId,
                dedupKey(NotificationEvent.REFUND_APPLIED, subOrderId, receiverId));
    }

    /** 退款处理结果 → 通知买家（果农同意/拒绝、平台仲裁） */
    private void handleRefundToBuyer(JsonNode node, NotificationEvent event, String content) {
        Long buyerId = asLong(node, "buyerId");
        Long subOrderId = asLong(node, "subOrderId");
        String subOrderNo = node.path("subOrderNo").asText("");
        if (buyerId == null) {
            log.warn("refund notification missing buyerId, tag={}, subOrderNo={}", event.getTag(), subOrderNo);
            return;
        }
        notificationService.push(buyerId, NotificationType.ORDER, event.getTitle(),
                content + "（" + subOrderNo + "）", subOrderId,
                dedupKey(event, subOrderId, buyerId));
    }

    /**
     * 处理预售成熟通知（PRESALE_NOTIFY：批量携带预约用户）。
     */
    public void handlePresaleMatured(JsonNode node) {
        Long presaleId = asLong(node, "presaleId");
        Long productId = asLong(node, "productId");
        JsonNode userIds = node.get("userIds");
        if (presaleId == null || userIds == null || !userIds.isArray()) {
            log.warn("presale notification missing presaleId/userIds, keys={}", node);
            return;
        }
        String productTitle = productTitle(productId);
        String content = "您预约的「" + productTitle + "」已成熟，快去下单吧";
        List<Notification> batch = new ArrayList<>();
        for (JsonNode userNode : userIds) {
            long userId = userNode.asLong(0L);
            if (userId <= 0) {
                continue;
            }
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setType(NotificationType.PRESALE.getCode());
            notification.setTitle(NotificationEvent.PRESALE_MATURED.getTitle());
            notification.setContent(content);
            notification.setRelatedId(presaleId);
            notification.setIsRead(0);
            notification.setDedupKey(dedupKey(NotificationEvent.PRESALE_MATURED, presaleId, userId));
            batch.add(notification);
        }
        int inserted = notificationService.pushBatch(batch);
        log.info("presale matured notification consumed, presaleId={}, receivers={}, inserted={}",
                presaleId, batch.size(), inserted);
    }

    private void handleShipped(JsonNode node) {
        Long buyerId = asLong(node, "buyerId");
        Long subOrderId = asLong(node, "subOrderId");
        String subOrderNo = node.path("subOrderNo").asText("");
        String company = node.path("logisticsCompany").asText("");
        String logisticsNo = node.path("logisticsNo").asText("");
        if (buyerId == null) {
            log.warn("shipped notification missing buyerId, subOrderNo={}", subOrderNo);
            return;
        }
        String content = "您的订单（" + subOrderNo + "）已发货，物流：" + company + " " + logisticsNo + "，请注意查收";
        notificationService.push(buyerId, NotificationType.ORDER, NotificationEvent.ORDER_SHIPPED.getTitle(),
                content, subOrderId, dedupKey(NotificationEvent.ORDER_SHIPPED, subOrderId, buyerId));
    }

    private void handleReceived(JsonNode node) {
        Long farmerId = asLong(node, "farmerId");
        Long subOrderId = asLong(node, "subOrderId");
        String subOrderNo = node.path("subOrderNo").asText("");
        if (farmerId == null) {
            log.warn("received notification missing farmerId, subOrderNo={}", subOrderNo);
            return;
        }
        Long receiverId = findFarmerUserId(farmerId);
        if (receiverId == null) {
            log.warn("farmer not found, skip received notification, farmerId={}", farmerId);
            return;
        }
        String content = "订单（" + subOrderNo + "）买家已确认收货，交易完成";
        notificationService.push(receiverId, NotificationType.ORDER, NotificationEvent.ORDER_RECEIVED.getTitle(),
                content, subOrderId, dedupKey(NotificationEvent.ORDER_RECEIVED, subOrderId, receiverId));
    }

    private void handleReviewReplied(JsonNode node) {
        Long buyerId = asLong(node, "buyerId");
        Long reviewId = asLong(node, "reviewId");
        Long productId = asLong(node, "productId");
        if (buyerId == null) {
            log.warn("review replied notification missing buyerId, reviewId={}", reviewId);
            return;
        }
        String content = "果农已回复您在「" + productTitle(productId) + "」下的评价，快去看看";
        notificationService.push(buyerId, NotificationType.ORDER, NotificationEvent.REVIEW_REPLIED.getTitle(),
                content, reviewId, dedupKey(NotificationEvent.REVIEW_REPLIED, reviewId, buyerId));
    }

    private Long findFarmerUserId(Long farmerId) {
        if (farmerId == null) {
            return null;
        }
        Farmer farmer = farmerMapper.selectById(farmerId);
        return farmer == null ? null : farmer.getUserId();
    }

    private String productTitle(Long productId) {
        if (productId == null) {
            return "商品";
        }
        Product product = productMapper.selectById(productId);
        return product == null ? "商品" : product.getTitle();
    }

    /**
     * 幂等键：同一事件对同一接收人只落一条通知。
     */
    private String dedupKey(NotificationEvent event, Long relatedId, Long userId) {
        return event.getTag() + ":" + (relatedId == null ? 0L : relatedId) + ":" + userId;
    }

    private Long asLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }
}
