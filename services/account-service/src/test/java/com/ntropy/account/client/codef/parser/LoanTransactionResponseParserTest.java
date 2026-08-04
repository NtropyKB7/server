package com.ntropy.account.client.codef.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.account.client.codef.parser.LoanTransactionResponseParser.ParsedLoan;
import com.ntropy.account.domain.AccountTransactionCategory;
import com.ntropy.account.domain.entity.AccountTransaction;

class LoanTransactionResponseParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesLoanDetailAndPrincipalInterestHistory() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {
                  "resAccountStartDate": "20200101",
                  "resAccountEndDate": "20300101",
                  "resDatePayment": "20260205",
                  "resPrincipal": "120,000,000",
                  "resLoanKind": "주택담보대출",
                  "resLoanBalance": "95,000,000",
                  "resState": "정상",
                  "resRate": "3.45",
                  "commStartDate": "20250101",
                  "commEndDate": "20260131",
                  "resTrHistoryList": [
                    {
                      "commStartDate": "20251206",
                      "commEndDate": "20260105",
                      "resAccountTrDate": "20260105",
                      "resTransTypeNm": "원리금상환",
                      "resTranAmount": "1,200,000",
                      "resPrincipal": "900,000",
                      "resInterest": "300,000",
                      "resInterestRate": "3.45",
                      "resType": "정상이자",
                      "resLoanBalance": "95,000,000"
                    }
                  ]
                }
                """);

        List<ParsedLoan> parsed = LoanTransactionResponseParser.parse(data, 77L);

        assertEquals(1, parsed.size());
        ParsedLoan result = parsed.get(0);
        assertEquals(new BigDecimal("95000000"), result.detail().getBalance());
        assertEquals(LocalDate.of(2026, 2, 5), result.detail().getNextPaymentDate());

        assertEquals(1, result.transactions().size());
        AccountTransaction tx = result.transactions().get(0);
        assertEquals(AccountTransactionCategory.LOAN, tx.getTransactionCategory());
        assertEquals(LocalDate.of(2026, 1, 5), tx.getTranDate());
        assertEquals(new BigDecimal("1200000"), tx.getOutAmount());
        assertEquals(new BigDecimal("95000000"), tx.getAfterBalance());
        assertNotNull(tx.getFingerprint());
    }
}
