package com.ntropy.account.client.codef.parser;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.ntropy.account.domain.AccountGroup;
import com.ntropy.account.domain.AccountNoHash;
import com.ntropy.account.domain.entity.Account;

/**
 * CODEF 개인 보유계좌 조회({@code /v1/kr/bank/p/account/account-list}) 응답의 {@code data}를
 * {@link Account} 목록으로 변환한다.
 * 계좌 원문 번호는 저장 대상이 아니지만 같은 요청 흐름 안에서 거래내역 조회에 바로 써야 하므로
 * {@link ParsedAccount}에 잠시 담아 반환한다.
 */
public final class AccountResponseParser {

    private AccountResponseParser() {
    }

    public record ParsedAccount(Account account, String rawAccountNo) {
    }

    public static List<ParsedAccount> parse(JsonNode data, Long codefConnectionId, Long userId,
                                             String organizationCode) {
        List<ParsedAccount> result = new ArrayList<>();
        for (AccountGroup group : AccountGroup.values()) {
            JsonNode accounts = data.path(group.getResponseFieldName());
            if (!accounts.isArray()) {
                continue;
            }
            for (JsonNode accountNode : accounts) {
                result.add(parseOne(accountNode, group, codefConnectionId, userId, organizationCode));
            }
        }
        return result;
    }

    private static ParsedAccount parseOne(JsonNode node, AccountGroup group, Long codefConnectionId,
                                          Long userId, String organizationCode) {
        String rawAccountNo = CodefJsonSupport.text(node, "resAccount");
        if (rawAccountNo == null) {
            throw new IllegalStateException("CODEF 보유계좌 응답에 resAccount가 없습니다");
        }

        Account account = new Account();
        account.setCodefConnectionId(codefConnectionId);
        account.setUserId(userId);
        account.setOrganizationCode(organizationCode);
        account.setAccountGroup(group);
        account.setDepositTypeCode(CodefJsonSupport.text(node, "resAccountDeposit"));
        account.setAccountNoMasked(CodefJsonSupport.text(node, "resAccountDisplay"));
        account.setAccountNoHash(AccountNoHash.hash(organizationCode, rawAccountNo));

        String nickName = CodefJsonSupport.text(node, "resAccountNickName");
        String productName = CodefJsonSupport.text(node, "resAccountName");
        account.setAccountName(nickName != null ? nickName : productName);

        account.setBalance(CodefJsonSupport.amount(node, "resAccountBalance"));
        String currencyCode = CodefJsonSupport.text(node, "resAccountCurrency");
        account.setCurrencyCode(currencyCode != null ? currencyCode : "KRW");

        account.setAccountStartDate(CodefJsonSupport.date(node, "resAccountStartDate"));
        account.setAccountEndDate(CodefJsonSupport.date(node, "resAccountEndDate"));
        account.setLastTranDate(CodefJsonSupport.date(node, "resLastTranDate"));

        if (group == AccountGroup.DEPOSIT_TRUST) {
            account.setAccountLifetime(CodefJsonSupport.text(node, "resAccountLifetime"));
            account.setOverdraftYn(CodefJsonSupport.flag01(node, "resOverdraftAcctYN"));
            account.setLoanKind(CodefJsonSupport.text(node, "resLoanKind"));
            account.setLoanBalance(CodefJsonSupport.amount(node, "resLoanBalance"));
            account.setLoanStartDate(CodefJsonSupport.date(node, "resLoanStartDate"));
            account.setLoanEndDate(CodefJsonSupport.date(node, "resLoanEndDate"));
        } else if (group == AccountGroup.FUND) {
            account.setInvestedCost(CodefJsonSupport.amount(node, "resAccountInvestedCost"));
            account.setEarningsRate(CodefJsonSupport.amount(node, "resEarningsRate"));
        } else if (group == AccountGroup.LOAN) {
            account.setLoanExecNo(CodefJsonSupport.text(node, "resAccountLoanExecNo"));
        }

        return new ParsedAccount(account, rawAccountNo);
    }
}
