package com.ntropy.ai.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

/** 환경변수 기반 SMTP 설정. 비밀값에는 코드 기본값을 두지 않는다. */
@Getter
@Component
public class MailProperties {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String from;
    private final String fromName;

    public MailProperties(
            @Value("${MAIL_HOST:smtp.gmail.com}") String host,
            @Value("${MAIL_PORT:587}") int port,
            @Value("${MAIL_USERNAME:}") String username,
            @Value("${MAIL_PASSWORD:}") String password,
            @Value("${MAIL_FROM:}") String from,
            @Value("${MAIL_FROM_NAME:Ntropy}") String fromName
    ) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.from = from;
        this.fromName = fromName;
    }

    public boolean isComplete() {
        return hasText(host) && port > 0 && hasText(username) && hasText(password) && hasText(from);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
