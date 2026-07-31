package com.ntropy.common.exception;

public class ServiceException extends RuntimeException {

    private final int statusCode;
    private final String errorMessage;

    public ServiceException(ServiceErrorCode errorCode) {
        super(errorCode.getMessage());
        this.statusCode = errorCode.getStatusCode();
        this.errorMessage = errorCode.getMessage();
    }

    public ServiceException(ServiceErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + " " + detail);
        this.statusCode = errorCode.getStatusCode();
        this.errorMessage = errorCode.getMessage() + " " + detail;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}