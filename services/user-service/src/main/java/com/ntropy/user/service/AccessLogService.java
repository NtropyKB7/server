package com.ntropy.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.user.model.AccessLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "AUTH_LOG")
public class AccessLogService {

    private final ObjectMapper objectMapper;

    public void saveAccessLog(AccessLog accessLog) {
        try {
            String logJson = objectMapper.writeValueAsString(accessLog);
            log.info(logJson);
        } catch (JsonProcessingException e) {
            log.error("AccessLog JSON 변환 실패", e);
        }
    }
}