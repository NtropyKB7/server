package com.ntropy.account.client.codef.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.account.client.codef.parser.AccountResponseParser.ParsedAccount;
import com.ntropy.account.domain.AccountGroup;
import com.ntropy.account.domain.AccountNoHash;
import com.ntropy.account.domain.entity.Account;

class AccountResponseParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesDepositTrustAndFundGroupsDefensively() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {
                  "resDepositTrust": [
                    {
                      "resAccount": "110123456789",
                      "resAccountDisplay": "110-***-456789",
                      "resAccountDeposit": "11",
                      "resAccountBalance": "1,234,567",
                      "resAccountCurrency": "KRW",
                      "resAccountNickName": "생활비통장",
                      "resAccountName": "",
                      "resAccountStartDate": "20200101",
                      "resAccountEndDate": "",
                      "resLastTranDate": "20260101",
                      "resAccountLifetime": "",
                      "resOverdraftAcctYN": "0",
                      "resLoanKind": "",
                      "resLoanBalance": "",
                      "resLoanStartDate": "",
                      "resLoanEndDate": ""
                    }
                  ],
                  "resFund": [
                    {
                      "resAccount": "998877",
                      "resAccountDisplay": "998-***-877",
                      "resAccountDeposit": "30",
                      "resAccountBalance": "",
                      "resAccountCurrency": "",
                      "resAccountName": "글로벌성장펀드",
                      "resAccountInvestedCost": "",
                      "resEarningsRate": ""
                    }
                  ]
                }
                """);

        List<ParsedAccount> parsed = AccountResponseParser.parse(data, 1L, 100L, "0004");

        assertEquals(2, parsed.size());

        ParsedAccount depositTrust = parsed.get(0);
        assertEquals("110123456789", depositTrust.rawAccountNo());
        Account deposit = depositTrust.account();
        assertEquals(AccountGroup.DEPOSIT_TRUST, deposit.getAccountGroup());
        assertEquals("11", deposit.getDepositTypeCode());
        assertEquals("****6789", deposit.getAccountNoMasked());
        assertEquals(AccountNoHash.hash("0004", "110123456789"), deposit.getAccountNoHash());
        assertEquals("생활비통장", deposit.getAccountName());
        assertEquals(new BigDecimal("1234567"), deposit.getBalance());
        assertEquals("KRW", deposit.getCurrencyCode());
        assertNull(deposit.getAccountEndDate());
        assertFalse(deposit.getOverdraftYn());
        assertNull(deposit.getLoanKind());

        ParsedAccount fund = parsed.get(1);
        Account fundAccount = fund.account();
        assertEquals(AccountGroup.FUND, fundAccount.getAccountGroup());
        assertEquals("글로벌성장펀드", fundAccount.getAccountName());
        assertNull(fundAccount.getBalance());
        assertEquals("KRW", fundAccount.getCurrencyCode());
        assertNull(fundAccount.getInvestedCost());
        assertNull(fundAccount.getEarningsRate());
    }

    @Test
    void skipsGroupsMissingFromResponse() throws Exception {
        JsonNode data = objectMapper.readTree("{\"resDepositTrust\": []}");

        List<ParsedAccount> parsed = AccountResponseParser.parse(data, 1L, 100L, "0004");

        assertEquals(0, parsed.size());
    }
}
