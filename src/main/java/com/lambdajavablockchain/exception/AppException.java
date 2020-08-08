package com.lambdajavablockchain.exception;

public class AppException extends Exception {
    private static final long serialVersionUID = 1L;

    public AppException(String errorMessage) {
        super(errorMessage);
    }

    public AppException(String errorMessage, Throwable error) {
        super(errorMessage, error);
    }
}
