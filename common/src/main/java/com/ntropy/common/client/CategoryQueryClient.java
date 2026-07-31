package com.ntropy.common.client;

import java.util.List;

import com.ntropy.common.dto.work.summary.CategorySummary;

public interface CategoryQueryClient {

    List<CategorySummary> getCategories();

    CategorySummary getCategory(Long categoryId);
}
