package com.solace.aaron.restqueue;

public class ReturnValue {

    private final int httpReturnCode;
    private final String httpReturnReason;
    private final boolean isSuccess;
    private final Throwable cause;
    
    public ReturnValue(int httpReturnCode, String httpReturnReason, boolean isSuccess) {
        this(httpReturnCode,httpReturnReason,isSuccess,null);
    }

    public ReturnValue(int httpReturnCode, String httpReturnReason, boolean isSuccess, Throwable cause) {
        this.httpReturnCode = httpReturnCode;
        this.httpReturnReason = httpReturnReason;
        this.isSuccess = isSuccess;
        this.cause = cause;
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
    
    public Throwable getCause() {
        return cause;
    }
    
}
