package com.solace.aaron.restQ;

import java.util.HashMap;
import java.util.Map;

public class ReturnValue {

    private final int httpReturnCode;
    private final String httpReturnReason;
    private final boolean isSuccess;
    private final Throwable cause;
    private final Map<String,String> httpHeaders = new HashMap<>();
    
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
    
    public ReturnValue withHttpHeader(String key, String value) {
        httpHeaders.put("JMS_Solace_HTTP_field_"+key,value);
        return this;
    }
    
    public Map<String,String> getHttpHeaders() {
        return httpHeaders;
    }
    
}
