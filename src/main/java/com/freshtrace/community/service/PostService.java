package com.freshtrace.community.service;

import com.freshtrace.common.PageVO;
import com.freshtrace.community.dto.PostCreateDTO;
import com.freshtrace.community.dto.PostQueryDTO;
import com.freshtrace.community.vo.PostDetailVO;
import com.freshtrace.community.vo.PostVO;

public interface PostService {

    PostVO createPost(Long farmerId, PostCreateDTO dto);

    PageVO<PostVO> page(PostQueryDTO query, Long currentUserId);

    PostDetailVO detail(Long postId, Long currentUserId);

    void deletePost(Long farmerId, Long postId);
}
