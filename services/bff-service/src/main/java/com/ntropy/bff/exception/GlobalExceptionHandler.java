package com.ntropy.bff.exception;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.common.exception.ServiceException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ServiceException.class)
    public ApiResponse<Void> handleServiceException(ServiceException e) {
        return ApiResponse.fail(e);
    }
}