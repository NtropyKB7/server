package com.ntropy.bff.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 모든 bff 응답을 감싸는 공통 envelope.
 * {success, status_code, message, data} 형태를 API 명세 그대로 따른다.
 *
 * 필드명을 아예 status_code로 둬서 Lombok이 getStatus_code()/setStatus_code()를
 * 그대로 생성하게 했다 (JSON 키를 "status_code"로 맞추기 위해 @JsonProperty 없이
 * 필드명 자체로 해결).
 *
 * ⚠️ bff-service에 이미 동일한 역할을 하는 공통 응답 클래스가 있다면 그걸 쓰고
 * 이 파일은 지워주세요.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private boolean success;
    private int status_code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, 200, "요청에 성공하였습니다.", data);
    }

    public static <T> ApiResponse<T> success(int statusCode, String message, T data) {
        return new ApiResponse<>(true, statusCode, message, data);
    }

    public static <T> ApiResponse<T> fail(int statusCode, String message) {
        return new ApiResponse<>(false, statusCode, message, null);
    }
}