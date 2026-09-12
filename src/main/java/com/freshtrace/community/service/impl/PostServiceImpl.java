package com.freshtrace.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.common.PageVO;
import com.freshtrace.common.cache.CacheKeys;
import com.freshtrace.community.dto.PostCreateDTO;
import com.freshtrace.community.dto.PostQueryDTO;
import com.freshtrace.community.entity.Post;
import com.freshtrace.community.entity.PostImage;
import com.freshtrace.community.entity.PostLike;
import com.freshtrace.community.mapper.PostImageMapper;
import com.freshtrace.community.mapper.PostLikeMapper;
import com.freshtrace.community.mapper.PostMapper;
import com.freshtrace.community.service.PostCommentService;
import com.freshtrace.community.service.PostService;
import com.freshtrace.community.vo.PostDetailVO;
import com.freshtrace.community.vo.PostVO;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.product.service.ProductService;
import com.freshtrace.product.vo.ProductDetailVO;
import com.freshtrace.trace.entity.TraceNode;
import com.freshtrace.trace.mapper.TraceNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 动态帖子实现（Phase 6 Day 2）。
 * <p>
 * - 发布：事务内 INSERT t_post + 逐条 INSERT t_post_image，关联商品/溯源节点必须归属当前果农；
 * - 列表：分页查帖子后批量补全图片/果农果园名/点赞状态，避免 N+1；
 * - 删除：仅作者本人，逻辑删除帖子及其图片。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl implements PostService {

    private final PostMapper postMapper;
    private final PostImageMapper postImageMapper;
    private final PostLikeMapper postLikeMapper;
    private final PostCommentService postCommentService;
    private final FarmerMapper farmerMapper;
    private final ProductService productService;
    private final TraceNodeMapper traceNodeMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional
    public PostVO createPost(Long farmerId, PostCreateDTO dto) {
        requireProductOwnerIfPresent(farmerId, dto.getProductId());
        requireTraceNodeOwnerIfPresent(farmerId, dto.getTraceNodeId());

        Post post = new Post();
        post.setFarmerId(farmerId);
        post.setProductId(dto.getProductId());
        post.setTraceNodeId(dto.getTraceNodeId());
        post.setContent(dto.getContent());
        post.setLikeCount(0);
        post.setCommentCount(0);
        postMapper.insert(post);

        List<String> images = dto.getImages();
        if (images != null && !images.isEmpty()) {
            int sortOrder = 0;
            for (String imageUrl : images) {
                PostImage image = new PostImage();
                image.setPostId(post.getId());
                image.setImageUrl(imageUrl);
                image.setSortOrder(sortOrder++);
                postImageMapper.insert(image);
            }
        }
        evictFarmerHome(farmerId);
        return assemble(List.of(post), null).get(0);
    }

    @Override
    public PageVO<PostVO> page(PostQueryDTO query, Long currentUserId) {
        Page<Post> page = new Page<>(query.getPage(), query.getSize());
        postMapper.selectPage(page, new LambdaQueryWrapper<Post>()
                .eq(query.getFarmerId() != null, Post::getFarmerId, query.getFarmerId())
                .orderByDesc(Post::getId));
        List<PostVO> records = assemble(page.getRecords(), currentUserId);
        return PageVO.of(page, records);
    }

    @Override
    public PostDetailVO detail(Long postId, Long currentUserId) {
        Post post = requirePost(postId);
        PostVO base = assemble(List.of(post), currentUserId).get(0);
        PostDetailVO detail = new PostDetailVO();
        BeanUtils.copyProperties(base, detail);
        detail.setComments(postCommentService.listTree(postId));
        return detail;
    }

    @Override
    @Transactional
    public void deletePost(Long farmerId, Long postId) {
        Post post = requirePost(postId);
        if (!post.getFarmerId().equals(farmerId)) {
            throw new BizException(ErrorCode.POST_PERMISSION_DENIED);
        }
        postMapper.deleteById(postId);
        postImageMapper.delete(new LambdaQueryWrapper<PostImage>().eq(PostImage::getPostId, postId));
        evictFarmerHome(post.getFarmerId());
    }

    /**
     * 失效果农主页缓存（发帖/删帖改变主页动态预览）。
     */
    private void evictFarmerHome(Long farmerId) {
        try {
            stringRedisTemplate.delete(CacheKeys.farmerHome(farmerId));
        } catch (Exception e) {
            log.warn("invalidate farmer home cache failed, farmerId={}", farmerId, e);
        }
    }

    /**
     * 批量组装 VO：一次查出图片、果农果园名、当前用户点赞集合，避免逐条查询 N+1。
     */
    private List<PostVO> assemble(List<Post> posts, Long currentUserId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = posts.stream().map(Post::getId).toList();

        Map<Long, List<String>> imageMap = postImageMapper.selectList(new LambdaQueryWrapper<PostImage>()
                        .in(PostImage::getPostId, postIds)
                        .orderByAsc(PostImage::getSortOrder)
                        .orderByAsc(PostImage::getId))
                .stream()
                .collect(Collectors.groupingBy(PostImage::getPostId,
                        Collectors.mapping(PostImage::getImageUrl, Collectors.toList())));

        List<Long> farmerIds = posts.stream().map(Post::getFarmerId).distinct().toList();
        Map<Long, String> orchardMap = farmerMapper.selectBatchIds(farmerIds).stream()
                .collect(Collectors.toMap(Farmer::getId, f -> f.getOrchardName() == null ? "" : f.getOrchardName(), (a, b) -> a));

        Set<Long> likedPostIds = new HashSet<>();
        if (currentUserId != null) {
            postLikeMapper.selectList(new LambdaQueryWrapper<PostLike>()
                            .in(PostLike::getPostId, postIds)
                            .eq(PostLike::getUserId, currentUserId))
                    .forEach(like -> likedPostIds.add(like.getPostId()));
        }

        return posts.stream()
                .map(post -> toVO(post,
                        imageMap.getOrDefault(post.getId(), List.of()),
                        orchardMap.get(post.getFarmerId()),
                        likedPostIds.contains(post.getId())))
                .toList();
    }

    private Post requirePost(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private void requireProductOwnerIfPresent(Long farmerId, Long productId) {
        if (productId == null) {
            return;
        }
        ProductDetailVO detail = productService.detail(productId);
        if (!detail.getFarmerId().equals(farmerId)) {
            throw new BizException(ErrorCode.PRODUCT_PERMISSION_DENIED);
        }
    }

    private void requireTraceNodeOwnerIfPresent(Long farmerId, Long traceNodeId) {
        if (traceNodeId == null) {
            return;
        }
        TraceNode node = traceNodeMapper.selectById(traceNodeId);
        if (node == null) {
            throw new BizException(ErrorCode.TRACE_NODE_NOT_FOUND);
        }
        ProductDetailVO detail = productService.detail(node.getProductId());
        if (!detail.getFarmerId().equals(farmerId)) {
            throw new BizException(ErrorCode.PRODUCT_PERMISSION_DENIED);
        }
    }

    private PostVO toVO(Post post, List<String> images, String orchardName, boolean liked) {
        PostVO vo = new PostVO();
        vo.setId(post.getId());
        vo.setFarmerId(post.getFarmerId());
        vo.setOrchardName(orchardName);
        vo.setProductId(post.getProductId());
        vo.setTraceNodeId(post.getTraceNodeId());
        vo.setContent(post.getContent());
        vo.setLikeCount(post.getLikeCount());
        vo.setCommentCount(post.getCommentCount());
        vo.setImages(images);
        vo.setLiked(liked);
        vo.setCreatedAt(post.getCreateTime());
        return vo;
    }
}
