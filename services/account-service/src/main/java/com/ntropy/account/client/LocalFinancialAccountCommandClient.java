package com.ntropy.account.client;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.ntropy.account.domain.ConnectionProvider;
import com.ntropy.account.domain.InstitutionKeys;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.exception.AccountErrorCode;
import com.ntropy.account.mapper.AccountLifecycleMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;
import com.ntropy.account.service.AccountCollectionService;
import com.ntropy.common.client.FinancialAccountCommandClient;
import com.ntropy.common.client.UserBirthDateQueryClient;
import com.ntropy.common.dto.account.AccountRegistrationCommand;
import com.ntropy.common.dto.account.AccountRegistrationSummary;
import com.ntropy.common.dto.account.BankSummary;
import com.ntropy.common.dto.account.MyDataConnectionSummary;
import com.ntropy.common.exception.ServiceException;

import lombok.RequiredArgsConstructor;

/** BFF에 노출할 금융계좌 명령과 연결 상태를 account-service 기능으로 조합한다. */
@Component
@RequiredArgsConstructor
public class LocalFinancialAccountCommandClient implements FinancialAccountCommandClient {

    private static final int DEFAULT_COLLECTION_DAYS = 90;

    private final AccountCollectionService accountCollectionService;
    private final AccountLifecycleMapper accountLifecycleMapper;
    private final CodefConnectionMapper codefConnectionMapper;
    private final ObjectProvider<UserBirthDateQueryClient> userBirthDateQueryClientProvider;

    @Override
    public List<BankSummary> findSupportedBanks() {
        return Arrays.stream(PersonalBank.values())
                .map(LocalFinancialAccountCommandClient::toBankSummary)
                .toList();
    }

    @Override
    public MyDataConnectionSummary findMyDataStatus(Long userId) {
        requirePositive(userId, "userId");
        CodefConnection connection = codefConnectionMapper.findByUserIdAndProvider(
                userId, ConnectionProvider.CODEF.name()
        );
        if (connection == null || connection.getConnectedId() == null || connection.getConnectedId().isBlank()) {
            return new MyDataConnectionSummary(false, List.of(), null);
        }

        List<BankSummary> connectedBanks = InstitutionKeys.parse(connection.getRegisteredInstitutionKeys()).stream()
                .map(LocalFinancialAccountCommandClient::resolveBank)
                .map(LocalFinancialAccountCommandClient::toBankSummary)
                .toList();
        return new MyDataConnectionSummary(true, connectedBanks, connection.getUpdatedAt());
    }

    @Override
    public AccountRegistrationSummary registerAccount(Long userId, AccountRegistrationCommand command) {
        requirePositive(userId, "userId");
        if (command == null) {
            throw new ServiceException(AccountErrorCode.INVALID_REQUEST, "요청 본문이 필요합니다.");
        }
        PersonalBank bank = resolveBank(command.organizationCode());
        String connectionType = normalizeConnectionType(command.connectionType());

        if ("VIRTUAL".equals(connectionType)) {
            // NTROPY 가상 금융데이터 리팩터링(이슈 #84~) 진행 중에는 VIRTUAL 등록을 차단한다.
            // 스키마·조회 계약·생성기 전체 검증이 끝나면 이 분기를 다시 활성화한다.
            throw new ServiceException(AccountErrorCode.VIRTUAL_REGISTRATION_BLOCKED);
        }

        requireNonBlank(command.bankLoginId(), bank.getDisplayName() + " 로그인 ID");
        requireNonBlank(command.bankLoginPassword(), bank.getDisplayName() + " 로그인 비밀번호");
        String birthDate = findBirthDateIfRequired(userId, bank);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(DEFAULT_COLLECTION_DAYS - 1L);
        int accountCount = accountCollectionService.registerAndCollect(
                userId, bank, command.bankLoginId(), command.bankLoginPassword(), birthDate, startDate, endDate
        ).size();
        return new AccountRegistrationSummary(connectionType, bank.getOrganizationCode(), accountCount);
    }

    @Override
    public void deactivateAccount(Long userId, Long accountId) {
        requirePositive(userId, "userId");
        requirePositive(accountId, "accountId");
        if (accountLifecycleMapper.deactivateByIdAndUserId(accountId, userId) == 0) {
            throw new ServiceException(AccountErrorCode.ACCOUNT_NOT_FOUND);
        }
    }

    private String findBirthDateIfRequired(Long userId, PersonalBank bank) {
        if (!bank.isBirthDateRequired()) {
            return null;
        }
        UserBirthDateQueryClient client = userBirthDateQueryClientProvider.getIfAvailable();
        if (client == null) {
            throw new ServiceException(
                    AccountErrorCode.INVALID_REQUEST,
                    "해당 은행 연결에는 회원 생년월일 조회 연동이 필요합니다."
            );
        }
        String birthDate = client.findBirthDate(userId);
        try {
            return bank.normalizeBirthDate(birthDate);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(
                    AccountErrorCode.INVALID_REQUEST,
                    "회원정보에 유효한 생년월일(YYYYMMDD)이 필요합니다."
            );
        }
    }

    private static String normalizeConnectionType(String connectionType) {
        if (connectionType == null) {
            throw new ServiceException(AccountErrorCode.INVALID_REQUEST, "connectionType이 필요합니다.");
        }
        String normalized = connectionType.trim().toUpperCase(java.util.Locale.ROOT);
        if (!"VIRTUAL".equals(normalized) && !"CODEF".equals(normalized)) {
            throw new ServiceException(
                    AccountErrorCode.INVALID_REQUEST,
                    "connectionType은 VIRTUAL 또는 CODEF여야 합니다."
            );
        }
        return normalized;
    }

    private static PersonalBank resolveBank(String organizationCode) {
        try {
            return PersonalBank.fromOrganizationCode(organizationCode);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(AccountErrorCode.UNSUPPORTED_BANK, organizationCode);
        }
    }

    private static BankSummary toBankSummary(PersonalBank bank) {
        return new BankSummary(
                bank.getOrganizationCode(), bank.getDisplayName(), bank.isBirthDateRequired(),
                true, true, bank != PersonalBank.SC_BANK
        );
    }

    private static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new ServiceException(AccountErrorCode.INVALID_REQUEST, fieldName + "는 양수여야 합니다.");
        }
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ServiceException(AccountErrorCode.INVALID_REQUEST, fieldName + "가 필요합니다.");
        }
    }
}
