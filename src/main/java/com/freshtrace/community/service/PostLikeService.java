package com.freshtrace.community.service;

public interface PostLikeService {

    void like(Long userId, Long postId);

    void unlike(Long userId, Long postId);
}
