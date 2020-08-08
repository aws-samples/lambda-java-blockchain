package com.lambdajavablockchain.model;

import org.hyperledger.fabric.sdk.Enrollment;

import java.security.PrivateKey;

/**
 * FabricEnrollment class holding the private key and certificate of a Fabric User
 */

public class FabricEnrollment implements Enrollment {
    private PrivateKey key;
    private String cert;

    public FabricEnrollment() {
    }

    public FabricEnrollment(PrivateKey key, String cert) {
        this.key = key;
        this.cert = cert;
    }

    public void setKey(PrivateKey key) {
        this.key = key;
    }

    public void setCert(String cert) {
        this.cert = cert;
    }

    @Override
    public PrivateKey getKey() {
        return key;
    }

    @Override
    public String getCert() {
        return cert;
    }
}
