package com.lambdajavablockchain.model;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Class representing the payload of chaincode invoke request
 */
public class InvokeRequest {
    @NotNull
    private String chaincodeName;

    @NotNull
    private String functionName;

    private List<String> argList;

    public String getChaincodeName() {
        return chaincodeName;
    }

    public void setChaincodeName(String chaincodeName) {
        this.chaincodeName = chaincodeName;
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public List<String> getArgList() {
        return argList;
    }

    public void setArgList(List<String> argList) {
        this.argList = argList;
    }

    @Override
    public String toString() {
        return "InvokeRequest{" +
                "chaincodeName='" + chaincodeName + '\'' +
                ", functionName='" + functionName + '\'' +
                ", args=" + argList +
                '}';
    }
}
