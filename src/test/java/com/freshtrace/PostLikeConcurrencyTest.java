package com.freshtrace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshtrace.community.dto.PostCreateDTO;
import com.freshtrace.community.entity.Post;
import com.freshtrace.community.entity.PostLike;
import com.freshtrace.community.mapper.PostLikeMapper;
import com.freshtrace.community.mapper.PostMapper;
import com.freshtrace.community.service.PostLikeService;
import com.freshtrace.community.service.PostService;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 点赞并发测试（Phase 6 Day 5）：
 * 1. 不同用户并发点赞 → 计数与事实表行数均等于用户数；
 * 2. 同一用户并发点赞 → UNIQUE(post_id,user_id) 兜底 + 幂等，最多一行、计数为 1。
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PostLikeConcurrencyTest {

    @Autowired
    private PostService postService;

    @Autowired
    private PostLikeService postLikeService;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private PostLikeMapper postLikeMapper;

    @Autowired
    private FarmerMapper farmerMapper;

    @Autowired
    private UserMapper userMapper;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    @Test
    void concurrentLikesFromDistinctUsersCounted() throws Exception {
        long postId = createPost();
        int users = 10;
        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            userIds.add(800000L + i);
        }

        runConcurrently(users, index -> postLikeService.like(userIds.get(index), postId));

        Post post = postMapper.selectById(postId);
        assertThat(post.getLikeCount()).isEqualTo(users);
        assertThat(postLikeMapper.selectCount(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getPostId, postId))).isEqualTo(users);
    }

    @Test
    void concurrentDuplicateLikeBySameUserCountsOnce() throws Exception {
        long postId = createPost();
        long userId = 700001L;
        int threads = 10;

        runConcurrently(threads, index -> postLikeService.like(userId, postId));

        Post post = postMapper.selectById(postId);
        assertThat(post.getLikeCount()).isEqualTo(1);
        assertThat(postLikeMapper.selectCount(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getPostId, postId)
                .eq(PostLike::getUserId, userId))).isEqualTo(1);
    }

    private void runConcurrently(int threads, java.util.function.IntConsumer action) throws Exception {
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        action.accept(index);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private long createPost() {
        long n = seq.incrementAndGet();
        User user = new User();
        user.setUsername("farmer_" + n);
        user.setPasswordHash("x");
        user.setPhone("139" + String.format("%08d", n % 100000000));
        user.setRole(0);
        user.setStatus(1);
        userMapper.insert(user);

        Farmer farmer = new Farmer();
        farmer.setUserId(user.getId());
        farmer.setRealName("张三");
        farmer.setIdCard("encrypted");
        farmer.setOrchardName("测试果园");
        farmer.setOrchardProvince("广东省");
        farmer.setOrchardCity("深圳市");
        farmer.setOrchardDistrict("南山区");
        farmer.setOrchardAddress("某村1号");
        farmer.setAuditStatus(1);
        farmerMapper.insert(farmer);

        PostCreateDTO dto = new PostCreateDTO();
        dto.setContent("并发点赞测试");
        return postService.createPost(farmer.getId(), dto).getId();
    }
}
