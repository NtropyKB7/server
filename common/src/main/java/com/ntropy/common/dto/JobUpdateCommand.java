package com.ntropy.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 잡 수정 요청. userId(소유자 변경 불가)와 스케줄(#3에서 별도 처리 예정)은 포함하지 않음.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class JobUpdateCommand {

    private Long categoryId;
    private String jobName;
    private String settlementType;
    private Integer hourlyWage;
    private Integer monthlyWage;
    private Integer perTaskWage;
    private Float taskPerHour;
    private Boolean isRegular;
    private Integer baseFatigue;
}
