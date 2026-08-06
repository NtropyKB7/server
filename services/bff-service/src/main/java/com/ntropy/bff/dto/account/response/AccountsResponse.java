package com.ntropy.bff.dto.account.response;

import java.util.List;

import com.ntropy.common.dto.account.AccountSummary;

public record AccountsResponse(List<AccountSummary> accounts) {
}
