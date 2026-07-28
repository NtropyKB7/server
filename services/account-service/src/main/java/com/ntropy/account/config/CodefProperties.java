package com.ntropy.account.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class CodefProperties {

    @Value("${codef.sandbox.client-id}")
    private String sandboxClientId;

    @Value("${codef.sandbox.client-secret}")
    private String sandboxClientSecret;
}
