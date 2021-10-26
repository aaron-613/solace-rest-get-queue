package com.solace.aaron.restqueue;

public class ReturnValue {

    private final int httpReturnCode;
    private final String httpReturnReason;
    private final boolean isSuccess;
    
    public ReturnValue(int httpReturnCode, String httpReturnReason, boolean isSuccess) {
        this.httpReturnCode = httpReturnCode;
        this.httpReturnReason = httpReturnReason;
        this.isSuccess = isSuccess;
    }

    public int getHttpReturnCode() {
        return httpReturnCode;
    }

    public String getHttpReturnReason() {
        return httpReturnReason;
    }

    public boolean isSuccess() {
        return isSuccess;
    }
    
    
    
}
