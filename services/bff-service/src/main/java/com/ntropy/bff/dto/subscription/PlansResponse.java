package com.ntropy.bff.dto.subscription;

import com.ntropy.common.dto.PlanSummary;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * GET /api/subscriptions/plans 의 data 필드 모양.
 * { "plans": [ ... ] } 형태로 감싸기 위한 wrapper.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlansResponse {

    private List<PlanSummary> plans;
}