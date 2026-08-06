package com.ntropy.common.dto.account;

import java.util.List;

/** 서비스 모듈 사이에서 사용하는 페이지 조회 결과. */
public record PageSummary<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static <T> PageSummary<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageSummary<>(List.copyOf(content), page, size, totalElements, totalPages, page + 1 < totalPages);
    }
}
