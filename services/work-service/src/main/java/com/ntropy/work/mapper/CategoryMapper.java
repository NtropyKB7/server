package com.ntropy.work.mapper;

import java.util.List;

import com.ntropy.work.domain.entity.Category;

public interface CategoryMapper {

    void insert(Category category);

    Category findById(Long categoryId);

    List<Category> findAll();

    void update(Category category);

    void deleteById(Long categoryId);
}
