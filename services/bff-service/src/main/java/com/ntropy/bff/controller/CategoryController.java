package com.ntropy.bff.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.work.response.CategoriesResponse;
import com.ntropy.bff.response.ApiResponse;
import com.ntropy.common.client.CategoryQueryClient;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryQueryClient categoryQueryClient;

    @GetMapping
    public ApiResponse<CategoriesResponse> getCategories() {
        return ApiResponse.success(new CategoriesResponse(categoryQueryClient.getCategories()));
    }
}
