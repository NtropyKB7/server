package com.ntropy.work.domain.entity;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Job {

    private Long jobId;
    private Long userId;
    private Long categoryId;
    private String rewardType;
    private Integer hourlyWage;
    private Integer perTaskAmount;
    private String scheduleType;
    private Integer baseFatigue;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isActive;
}
