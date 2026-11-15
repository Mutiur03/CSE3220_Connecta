package com.connecta.backend.common.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** One page of a list: the items plus the paging facts a client needs to ask for the next page. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public static <E, T> PageResponse<T> from(Page<E> page, Function<? super E, ? extends T> mapper) {
        List<T> content = page.getContent().stream().<T>map(mapper).toList();
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
