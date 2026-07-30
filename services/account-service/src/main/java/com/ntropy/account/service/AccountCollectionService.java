package com.ntropy.account.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ntropy.account.client.codef.CodefBankTransactionClient;
import com.ntropy.account.client.codef.parser.AccountResponseParser;
import com.ntropy.account.client.codef.parser.AccountResponseParser.ParsedAccount;
import com.ntropy.account.client.codef.parser.AccountTransactionResponseParser;
import com.ntropy.account.domain.AccountGroup;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountTransaction;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.AccountMapper;
import com.ntropy.account.mapper.AccountTransactionMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;

import lombok.RequiredArgsConstructor;

/**
 * 은행 계정 등록부터 보유계좌·수시입출 거래내역 저장까지 조합한다.
 * 계좌 원문 번호는 저장하지 않으므로, 보유계좌 응답을 받은 바로 그 요청 흐름 안에서
 * 거래내역 조회까지 이어서 수행한다 (server/CLAUDE.local.md 참고).
 */
@Service
@RequiredArgsConstructor
public class AccountCollectionService {

    private static final String ORDINARY_DEPOSIT = "10";
    private static final String ORDINARY_WITHDRAWAL = "11";

    private final PersonalBankAccountService personalBankAccountService;
    private final CodefConnectionMapper codefConnectionMapper;
    private final CodefBankTransactionClient codefBankTransactionClient;
    private final AccountMapper accountMapper;
    private final AccountTransactionMapper accountTransactionMapper;

    public List<Account> registerAndCollect(Long userId, PersonalBank bank, String loginId, String rawPassword,
                                            String birthDate, LocalDate transactionStartDate,
                                            LocalDate transactionEndDate) {
        personalBankAccountService.registerPersonalAccount(userId, bank, loginId, rawPassword, birthDate);
        return collect(userId, bank, birthDate, transactionStartDate, transactionEndDate);
    }

    public List<Account> collect(Long userId, PersonalBank bank, String birthDate,
                                 LocalDate transactionStartDate, LocalDate transactionEndDate) {
        CodefConnection connection = codefConnectionMapper.findByUserId(userId);
        if (connection == null || connection.getConnectedId() == null
                || connection.getConnectedId().isBlank()) {
            throw new IllegalStateException("등록된 CODEF 연결이 없습니다");
        }
        String normalizedBirthDate = bank.normalizeBirthDate(birthDate);

        JsonNode accountListResponse = personalBankAccountService.getPersonalAccountList(userId, bank);
        List<ParsedAccount> parsedAccounts = AccountResponseParser.parse(
                accountListResponse.path("data"), connection.getId(), userId, bank.getOrganizationCode()
        );

        List<Account> savedAccounts = new ArrayList<>();
        for (ParsedAccount parsed : parsedAccounts) {
            accountMapper.upsert(parsed.account());
            Account saved = accountMapper.findByConnectionIdAndAccountNoHash(
                    connection.getId(), parsed.account().getAccountNoHash()
            );
            savedAccounts.add(saved);

            if (isTransactionEligible(saved, bank)) {
                collectTransactions(
                        bank, connection.getConnectedId(), parsed.rawAccountNo(), saved.getId(),
                        normalizedBirthDate, transactionStartDate, transactionEndDate
                );
            }
        }
        return savedAccounts;
    }

    private void collectTransactions(PersonalBank bank, String connectedId, String rawAccountNo, Long accountId,
                                     String birthDate, LocalDate startDate, LocalDate endDate) {
        JsonNode transactionListResponse = codefBankTransactionClient.getPersonalTransactionList(
                bank.getOrganizationCode(), connectedId, rawAccountNo, startDate, endDate, birthDate
        );
        List<AccountTransaction> transactions = AccountTransactionResponseParser.parse(
                transactionListResponse.path("data"), accountId
        );
        if (!transactions.isEmpty()) {
            accountTransactionMapper.insertAll(transactions);
        }
    }

    /**
     * 수시입출 거래내역 API 대상은 예금/신탁 그룹 중 미분류(10)·수시입출(11) 계좌뿐이다.
     * SC은행은 거래내역 조회에 accountPassword가 추가로 필요한데 이번 이슈에서는 다루지 않으므로 제외한다.
     */
    private static boolean isTransactionEligible(Account account, PersonalBank bank) {
        return account.getAccountGroup() == AccountGroup.DEPOSIT_TRUST
                && (ORDINARY_DEPOSIT.equals(account.getDepositTypeCode())
                        || ORDINARY_WITHDRAWAL.equals(account.getDepositTypeCode()))
                && bank != PersonalBank.SC_BANK;
    }
}
