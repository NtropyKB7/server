package com.ntropy.common.client;

import java.util.List;

import com.ntropy.common.dto.work.CategorySummary;

public interface CategoryQueryClient {

    List<CategorySummary> getCategories();

    CategorySummary getCategory(Long categoryId);
}
