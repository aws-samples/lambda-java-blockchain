package com.lambdajavablockchain.exception;

public class ManagedBlockchainServiceException extends Exception {
    private static final long serialVersionUID = 1L;

    public ManagedBlockchainServiceException(String errorMessage) {
        super(errorMessage);
    }

    public ManagedBlockchainServiceException(String errorMessage, Throwable error) {
        super(errorMessage, error);
    }
}
