package com.ntropy.common.client;

import java.util.List;

import com.ntropy.common.dto.account.AccountRegistrationCommand;
import com.ntropy.common.dto.account.AccountRegistrationSummary;
import com.ntropy.common.dto.account.BankSummary;
import com.ntropy.common.dto.account.MyDataConnectionSummary;

/** 금융계좌 등록·비활성화와 연결 메타데이터를 제공하는 account-service 계약. */
public interface FinancialAccountCommandClient {

    List<BankSummary> findSupportedBanks();

    MyDataConnectionSummary findMyDataStatus(Long userId);

    AccountRegistrationSummary registerAccount(Long userId, AccountRegistrationCommand command);

    void deactivateAccount(Long userId, Long accountId);

    void activateAccount(Long userId, Long accountId);
}
