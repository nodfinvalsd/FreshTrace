package com.freshtrace.im.vo;

import lombok.Data;

import java.util.List;

/**
 * 历史消息分页结果（游标分页）。
 * <p>
 * 按消息 ID 倒序返回（最新在前），{@code nextCursor} 为当前页最旧一条的 ID；
 * 下一页把它作为 {@code lastId} 传入即可，避免 offset 分页在插入新消息时错位。
 */
@Data
public class ChatHistoryVO {

    private List<ChatMessageVO> records;

    /** 是否还有更早的消息 */
    private boolean hasMore;

    /** 下一页游标（本页最旧消息ID），无更多时为 null */
    private Long nextCursor;
}
