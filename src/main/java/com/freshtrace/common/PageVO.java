package com.freshtrace.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

/**
 * 通用分页响应结构。
 */
@Data
public class PageVO<T> {

    private List<T> records;

    private long total;

    private long page;

    private long size;

    public static <T> PageVO<T> of(IPage<?> source, List<T> records) {
        PageVO<T> vo = new PageVO<>();
        vo.setRecords(records);
        vo.setTotal(source.getTotal());
        vo.setPage(source.getCurrent());
        vo.setSize(source.getSize());
        return vo;
    }

    public static <T> PageVO<T> empty(long page, long size) {
        PageVO<T> vo = new PageVO<>();
        vo.setRecords(List.of());
        vo.setTotal(0);
        vo.setPage(page);
        vo.setSize(size);
        return vo;
    }
}
