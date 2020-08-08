package com.lambdajavablockchain.exception;

public class EnrollmentNotFoundException extends Exception {
    private static final long serialVersionUID = 1L;

    public EnrollmentNotFoundException(String errorMessage) {
        super(errorMessage);
    }

    public EnrollmentNotFoundException(String errorMessage, Throwable error) {
        super(errorMessage, error);
    }
}
