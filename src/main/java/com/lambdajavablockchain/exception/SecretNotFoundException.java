package com.lambdajavablockchain.exception;

public class SecretNotFoundException extends Exception {
    private static final long serialVersionUID = 1L;

    public SecretNotFoundException(String errorMessage) {
        super(errorMessage);
    }

    public SecretNotFoundException(String errorMessage, Throwable error) {
        super(errorMessage, error);
    }
}
