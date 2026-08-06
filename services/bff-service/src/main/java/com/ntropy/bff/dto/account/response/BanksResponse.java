package com.ntropy.bff.dto.account.response;

import java.util.List;

import com.ntropy.common.dto.account.BankSummary;

public record BanksResponse(List<BankSummary> banks) {
}
