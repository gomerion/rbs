package ru.ssau.rbs.dto.common;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> items;
    private long total;
    private int limit;
    private int offset;

    public static <T> PageResponse<T> of(List<T> items, long total, int limit, int offset) {
        return new PageResponse<>(items, total, limit, offset);
    }
}
