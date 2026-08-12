package com.ntropy.bff.dto.dashboard.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DashboardIncomeResponse {

    private Long amount;
    private Long target;
}
