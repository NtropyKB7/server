package com.ntropy.common.dto.work;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategorySummary {

    private Long categoryId;
    private String name;
}
