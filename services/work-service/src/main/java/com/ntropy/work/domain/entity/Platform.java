package com.ntropy.work.domain.entity;

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
public class Platform {

    private Long platformId;
    private Long jobId;
    private String platformName;
    private String depositName;
    private String settlementCycle;
    private Boolean isActive;
    private String settlementDay;
}
