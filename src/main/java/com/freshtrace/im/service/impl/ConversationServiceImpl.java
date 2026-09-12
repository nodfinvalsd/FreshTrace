package com.freshtrace.im.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.common.PageVO;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.im.dto.ConversationCreateDTO;
import com.freshtrace.im.dto.ConversationQueryDTO;
import com.freshtrace.im.entity.Conversation;
import com.freshtrace.im.mapper.ChatMessageMapper;
import com.freshtrace.im.mapper.ConversationMapper;
import com.freshtrace.im.service.ConversationService;
import com.freshtrace.im.support.ImIdentitySupport;
import com.freshtrace.im.vo.ConversationVO;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.mapper.ProductMapper;
import com.freshtrace.product.service.ProductService;
import com.freshtrace.product.vo.ProductVO;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 会话服务实现（Phase 8）。
 * <p>
 * 关键点：
 * <ul>
 *     <li>创建幂等：先按 (user_id, farmer_id) 查询，并发下由 UNIQUE 约束兜底并在冲突后回查；</li>
 *     <li>视角判定：登录用户若为已认证果农按 farmer_id 查，否则按 user_id 查，绝不信任客户端角色；</li>
 *     <li>列表装配：对端用户/果农、来源商品全部批量查询，避免 N+1。</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMapper conversationMapper;

    private final ChatMessageMapper chatMessageMapper;

    private final ProductMapper productMapper;

    private final UserMapper userMapper;

    private final FarmerMapper farmerMapper;

    private final ProductService productService;

    private final ImIdentitySupport identitySupport;

    /**
     * 创建（或复用）买家与果农的会话。
     * <p>
     * 步骤：校验果农可用且非本人店铺 → 校验来源商品归属 → 查询已有会话 →
     * 不存在则插入（UNIQUE 冲突时回查）→ 已有会话可补全来源商品 → 装配 VO。
     */
    @Override
    public ConversationVO create(Long userId, ConversationCreateDTO dto) {
        // 目标果农必须存在且已通过认证，否则无法作为会话对端
        Farmer farmer = identitySupport.requireAvailableFarmer(dto.getFarmerId());
        // 买家与果农互斥，正常流程不会命中；防御果农用自己的账号给自己的店铺发起会话
        if (farmer.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.IM_SELF_CONVERSATION);
        }
        validateProduct(dto.getProductId(), farmer.getId());

        Conversation conversation = selectByUserAndFarmer(userId, farmer.getId());
        if (conversation == null) {
            conversation = insertConversation(userId, farmer.getId(), dto.getProductId());
        } else if (conversation.getProductId() == null && dto.getProductId() != null) {
            // 会话已存在但此前没有来源商品，本次从商品详情进入则补记，便于列表展示来源
            Conversation patch = new Conversation();
            patch.setId(conversation.getId());
            patch.setProductId(dto.getProductId());
            conversationMapper.updateById(patch);
            conversation.setProductId(dto.getProductId());
        }

        Map<Long, ProductVO> productMap = dto.getProductId() == null
                ? Map.of()
                : loadProducts(List.of(dto.getProductId()));
        return toVO(conversation, userId, null, Map.of(), Map.of(farmer.getId(), farmer), productMap);
    }

    /**
     * 会话列表：果农视角按 farmer_id、买家视角按 user_id 过滤，最后消息时间倒序（新会话无消息排最后）。
     */
    @Override
    public PageVO<ConversationVO> list(Long userId, ConversationQueryDTO query) {
        Long viewerFarmerId = identitySupport.currentFarmerIdOrNull(userId);

        Page<Conversation> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        if (viewerFarmerId != null) {
            wrapper.eq(Conversation::getFarmerId, viewerFarmerId);
        } else {
            wrapper.eq(Conversation::getUserId, userId);
        }
        wrapper.orderByDesc(Conversation::getLastMessageAt).orderByDesc(Conversation::getId);
        conversationMapper.selectPage(page, wrapper);
        if (page.getRecords().isEmpty()) {
            return PageVO.empty(query.getPage(), query.getSize());
        }

        // 批量装配对端与来源商品，避免逐条查询造成 N+1
        Map<Long, User> userMap = loadUsers(page.getRecords().stream()
                .map(Conversation::getUserId).distinct().toList());
        Map<Long, Farmer> farmerMap = loadFarmers(page.getRecords().stream()
                .map(Conversation::getFarmerId).distinct().toList());
        Map<Long, ProductVO> productMap = loadProducts(page.getRecords().stream()
                .map(Conversation::getProductId).filter(java.util.Objects::nonNull).distinct().toList());

        List<ConversationVO> records = page.getRecords().stream()
                .map(conversation -> toVO(conversation, userId, viewerFarmerId, userMap, farmerMap, productMap))
                .toList();
        return PageVO.of(page, records);
    }

    /**
     * 归属校验：登录用户要么是会话买家(user_id)，要么是该会话果农的账号(t_farmer.user_id)。
     */
    @Override
    public Conversation requireMember(Long conversationId, Long userId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        if (conversation.getUserId().equals(userId)) {
            return conversation;
        }
        Farmer farmer = identitySupport.findByUserId(userId);
        if (farmer != null && farmer.getId().equals(conversation.getFarmerId())) {
            return conversation;
        }
        throw new BizException(ErrorCode.NOT_CONVERSATION_MEMBER);
    }

    /**
     * 标记已读：会话未读计数清零 + 对方消息批量置已读，两步同一事务，避免计数与明细不一致。
     */
    @Override
    @Transactional
    public void markRead(Long userId, Long conversationId) {
        Conversation conversation = requireMember(conversationId, userId);
        boolean viewerIsFarmer = !conversation.getUserId().equals(userId);
        if (viewerIsFarmer) {
            conversationMapper.resetUnreadFarmer(conversationId);
        } else {
            conversationMapper.resetUnreadUser(conversationId);
        }
        chatMessageMapper.markMessagesRead(conversationId, userId);
    }

    /**
     * 总未读数：果农按 farmer_id 汇总 unread_farmer，买家按 user_id 汇总 unread_user。
     */
    @Override
    public long unreadTotal(Long userId) {
        Long farmerId = identitySupport.currentFarmerIdOrNull(userId);
        return farmerId != null
                ? conversationMapper.sumUnreadFarmer(farmerId)
                : conversationMapper.sumUnreadUser(userId);
    }

    /**
     * 校验来源商品存在且属于目标果农（商品详情页发起的会话才携带 productId）。
     */
    private void validateProduct(Long productId, Long farmerId) {
        if (productId == null) {
            return;
        }
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        if (!farmerId.equals(product.getFarmerId())) {
            throw new BizException(ErrorCode.IM_PRODUCT_FARMER_MISMATCH);
        }
    }

    /**
     * 插入会话；并发下唯一键冲突说明已有会话，回查后复用，保证「同一对买卖双方只有一条会话」。
     */
    private Conversation insertConversation(Long userId, Long farmerId, Long productId) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setFarmerId(farmerId);
        conversation.setProductId(productId);
        conversation.setUnreadUser(0);
        conversation.setUnreadFarmer(0);
        try {
            conversationMapper.insert(conversation);
            return conversation;
        } catch (DuplicateKeyException e) {
            Conversation existing = selectByUserAndFarmer(userId, farmerId);
            if (existing == null) {
                throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND);
            }
            log.info("conversation concurrent create, reuse existing, userId={}, farmerId={}", userId, farmerId);
            return existing;
        }
    }

    private Conversation selectByUserAndFarmer(Long userId, Long farmerId) {
        return conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUserId, userId)
                .eq(Conversation::getFarmerId, farmerId));
    }

    /**
     * 组装会话视图：按视角决定「对端」是谁、展示哪一侧未读。
     *
     * @param viewerFarmerId 当前登录用户的 farmer.id（买家视角为 null）
     */
    private ConversationVO toVO(Conversation conversation, Long viewerUserId, Long viewerFarmerId,
                                Map<Long, User> userMap, Map<Long, Farmer> farmerMap,
                                Map<Long, ProductVO> productMap) {
        ConversationVO vo = new ConversationVO();
        vo.setId(conversation.getId());
        vo.setProductId(conversation.getProductId());
        vo.setLastMessage(conversation.getLastMessage());
        vo.setLastMessageAt(conversation.getLastMessageAt());
        vo.setCreateTime(conversation.getCreateTime());

        ProductVO product = conversation.getProductId() == null ? null : productMap.get(conversation.getProductId());
        if (product != null) {
            vo.setProductTitle(product.getTitle());
            vo.setProductImage(product.getMainImage());
        }

        boolean viewerIsFarmer = viewerFarmerId != null && viewerFarmerId.equals(conversation.getFarmerId());
        if (viewerIsFarmer) {
            // 果农视角：对端是买家，展示买家昵称/头像与果农未读数
            vo.setUnreadCount(conversation.getUnreadFarmer());
            vo.setPeerRole(0);
            vo.setPeerUserId(conversation.getUserId());
            User peer = userMap.get(conversation.getUserId());
            if (peer != null) {
                vo.setPeerName(peer.getNickname());
                vo.setPeerAvatar(peer.getAvatarUrl());
            }
        } else {
            // 买家视角：对端是果农，展示果园名与买家未读数
            vo.setUnreadCount(conversation.getUnreadUser());
            vo.setPeerRole(1);
            Farmer peer = farmerMap.get(conversation.getFarmerId());
            if (peer != null) {
                vo.setPeerUserId(peer.getUserId());
                vo.setPeerName(peer.getOrchardName());
            }
        }
        return vo;
    }

    private Map<Long, User> loadUsers(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Map<Long, Farmer> loadFarmers(List<Long> farmerIds) {
        if (farmerIds.isEmpty()) {
            return Map.of();
        }
        return farmerMapper.selectBatchIds(farmerIds).stream()
                .collect(Collectors.toMap(Farmer::getId, Function.identity()));
    }

    private Map<Long, ProductVO> loadProducts(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productService.batchBrief(productIds).stream()
                .collect(Collectors.toMap(ProductVO::getId, Function.identity()));
    }
}
