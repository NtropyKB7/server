package com.ntropy.account.client.codef;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ntropy.account.config.CodefProperties;
import com.ntropy.account.client.codef.dto.CodefConnectionCreateResponse;
import com.ntropy.account.client.codef.dto.CodefConnectionCreateResponse.AccountResult;
import com.ntropy.account.client.codef.support.RsaUtil;

import lombok.RequiredArgsConstructor;

/**
 * CODEF 계정 등록({@code /v1/account/create}) API를 호출해 connectedId를 발급받는다.
 * 환경 선택과 CODEF 고유의 인코딩/디코딩, 토큰 재시도는 {@link CodefApiClient}에 위임한다.
 */
@Component
@RequiredArgsConstructor
public class CodefConnectionClient {

    private static final String ACCOUNT_CREATE_PATH = "/v1/account/create";
    private static final String ACCOUNT_ADD_PATH = "/v1/account/add";
    private static final String ACCOUNT_UPDATE_PATH = "/v1/account/update";

    private final CodefProperties codefProperties;
    private final CodefApiClient codefApiClient;

    /**
     * @param organizationCode CODEF 기관코드 (예: 국민은행 0004)
     * @param businessType     BK(은행/저축은행), CD(카드), ST(증권), IS(보험)
     * @param clientType       P(개인), B(기업/법인), A(통합)
     * @param loginId          대상기관 로그인 아이디
     * @param rawPassword      대상기관 로그인 비밀번호 평문 (이 메서드 안에서 RSA 암호화 후 전송, 평문은 저장하지 않음)
     * @param birthDate        생년월일 (기관에 따라 필요, 없으면 null)
     * @return CODEF가 발급한 connectedId
     */
    public String createConnection(String organizationCode, String businessType, String clientType,
                                    String loginId, String rawPassword, String birthDate) {
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("accountList", List.of(createAccount(
                    organizationCode, businessType, clientType, loginId, rawPassword, birthDate
            )));

            CodefConnectionCreateResponse response = codefApiClient.post(
                    ACCOUNT_CREATE_PATH,
                    requestBody,
                    CodefConnectionCreateResponse.class
            );

            if (response.getResult() == null || !"CF-00000".equals(response.getResult().getCode())
                    || response.getData() == null || response.getData().getConnectedId() == null
                    || response.getData().getConnectedId().isBlank()) {
                String message = response.getResult() != null ? response.getResult().getMessage() : "알 수 없는 오류";
                throw new IllegalStateException("CODEF 계정 등록 실패: " + message);
            }
            validateInstitutionSuccess(response, organizationCode, "등록");
            return response.getData().getConnectedId();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("CODEF 계정 등록 요청 실패", e);
        }
    }

    /**
     * 이미 발급된 connectedId에 다른 기관의 개인 계정을 추가한다.
     */
    public void addConnection(String connectedId, String organizationCode,
                              String businessType, String clientType,
                              String loginId, String rawPassword, String birthDate) {
        changeConnection(
                ACCOUNT_ADD_PATH, "추가", connectedId, organizationCode,
                businessType, clientType, loginId, rawPassword, birthDate
        );
    }

    /**
     * 기존 connectedId에 등록된 기관의 로그인 정보를 갱신한다.
     */
    public void updateConnection(String connectedId, String organizationCode,
                                 String businessType, String clientType,
                                 String loginId, String rawPassword, String birthDate) {
        changeConnection(
                ACCOUNT_UPDATE_PATH, "수정", connectedId, organizationCode,
                businessType, clientType, loginId, rawPassword, birthDate
        );
    }

    private void changeConnection(String path, String action, String connectedId, String organizationCode,
                                  String businessType, String clientType,
                                  String loginId, String rawPassword, String birthDate) {
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("accountList", List.of(createAccount(
                    organizationCode, businessType, clientType, loginId, rawPassword, birthDate
            )));
            requestBody.put("connectedId", connectedId);

            CodefConnectionCreateResponse response = codefApiClient.post(
                    path,
                    requestBody,
                    CodefConnectionCreateResponse.class
            );
            if (response.getResult() == null || !"CF-00000".equals(response.getResult().getCode())) {
                String message = response.getResult() != null ? response.getResult().getMessage() : "알 수 없는 오류";
                throw new IllegalStateException("CODEF 계정 " + action + " 실패: " + message);
            }
            validateInstitutionSuccess(response, organizationCode, action);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("CODEF 계정 " + action + " 요청 실패", e);
        }
    }

    private Map<String, Object> createAccount(String organizationCode, String businessType,
                                              String clientType, String loginId,
                                              String rawPassword, String birthDate) throws Exception {
        String encryptedPassword = RsaUtil.encryptWithPublicKey(rawPassword, codefProperties.getPublicKey());

        Map<String, Object> account = new LinkedHashMap<>();
        account.put("countryCode", "KR");
        account.put("businessType", businessType);
        account.put("clientType", clientType);
        account.put("organization", organizationCode);
        account.put("loginType", "1");
        account.put("id", loginId);
        account.put("password", encryptedPassword);
        if (birthDate != null) {
            account.put("birthDate", birthDate);
        }
        return account;
    }

    private static void validateInstitutionSuccess(CodefConnectionCreateResponse response,
                                                    String organizationCode, String action) {
        if (response.getData() != null && response.getData().getSuccessList() != null) {
            boolean succeeded = response.getData().getSuccessList().stream()
                    .anyMatch(item -> organizationCode.equals(item.getOrganization())
                            && "CF-00000".equals(item.getCode()));
            if (succeeded) {
                return;
            }
        }

        AccountResult failure = findInstitutionResult(
                response.getData() != null ? response.getData().getErrorList() : null,
                organizationCode
        );
        String detail = failure == null
                ? "기관별 성공 결과가 없습니다"
                : failure.getCode() + " " + failure.getMessage();
        throw new IllegalStateException("CODEF 계정 " + action + " 실패: " + detail.trim());
    }

    private static AccountResult findInstitutionResult(List<AccountResult> results, String organizationCode) {
        if (results == null) {
            return null;
        }
        return results.stream()
                .filter(item -> organizationCode.equals(item.getOrganization()))
                .findFirst()
                .orElse(null);
    }

}
