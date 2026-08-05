//package com.ntropy.user.service;
//
//import com.ntropy.user.mapper.AccessLogMapper;
//import com.ntropy.user.model.AccessLog;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class AccessLogService {
//
//    private final AccessLogMapper accessLogMapper;
//
//    @Transactional
//    public void saveAccessLog(AccessLog accessLog) {
//        accessLogMapper.insertAccessLog(accessLog);
//        log.debug("Access Log 저장 완료: {}", accessLog.getEventType());
//    }
//}