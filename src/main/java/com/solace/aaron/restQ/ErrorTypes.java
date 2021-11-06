package com.solace.aaron.restQ;

public enum ErrorTypes {

    /** when trying to get a message with a queue that isn't bound or a flowId that doesn't exist */
    INVALID_FLOW_ID("invalid queue name or provided flow ID", 400),

    INVALID_URL_PARAMS("invalid combination of URL query parameters passed, please check docs", 400),

    FLOW_CLOSED_TIMEOUT("this flow has been closded due to inactivity", 501),
    NO_MESSAGES("no messages available on this flow", 404),
    FLOW_ALREADY_ACTIVE("a flow to this queue is already active", 400),
    INVALID_MSG_ID("provided msg ID invalid", 400),
    
    URL_PARAMS_NOT_EMPTY("URL query parameters must be empty", 400),
    ;
    
    
    final String message;
    final int code;
    
    private ErrorTypes(String reason, int reasonCode) {
        this.message = reason;
        this.code = reasonCode;
    }
    
    public String getReason() {
        return toString();
    }

    public String getMessage() {
        return message;
    }

    public int getCode() {
        return code;
    }
    
}
