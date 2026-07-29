package com.ntropy.account.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ntropy.account.config.CodefProperties;

import lombok.RequiredArgsConstructor;

/**
 * CODEF 계정 등록({@code /v1/account/create}) API를 호출해 connectedId를 발급받는다.
 * 환경 선택과 CODEF 고유의 인코딩/디코딩, 토큰 재시도는 {@link CodefApiClient}에 위임한다.
 */
@Component
@RequiredArgsConstructor
public class CodefAccountClient {

    private static final String ACCOUNT_CREATE_PATH = "/v1/account/create";

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

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("accountList", List.of(account));

            CodefAccountCreateResponse response = codefApiClient.post(
                    ACCOUNT_CREATE_PATH,
                    requestBody,
                    CodefAccountCreateResponse.class
            );

            if (response.getResult() == null || !"CF-00000".equals(response.getResult().getCode())
                    || response.getData() == null || response.getData().getConnectedId() == null
                    || response.getData().getConnectedId().isBlank()) {
                String message = response.getResult() != null ? response.getResult().getMessage() : "알 수 없는 오류";
                throw new IllegalStateException("CODEF 계정 등록 실패: " + message);
            }
            return response.getData().getConnectedId();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("CODEF 계정 등록 요청 실패", e);
        }
    }

}
