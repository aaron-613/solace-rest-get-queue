package com.solace.aaron.restqueue;

public enum ErrorTypes {

    /** when trying to get a message with a flowId that doesn't exist */
    INVALID_FLOW_ID("provided flow ID invalid", 400),
    
    FLOW_CLOSED_TIMEOUT("this flow has been closded due to inactivity", 501),
    NO_MESSAGES("no messages available on this flow", 404),
    FLOW_ALREADY_ACTIVE("a flow to this queue is already active", 400),
    INVALID_ACK_ID("provide ack ID invalid", 400),
    ;
    
    
    final String reason;
    final int reasonCode;
    
    private ErrorTypes(String reason, int reasonCode) {
        this.reason = reason;
        this.reasonCode = reasonCode;
    }
    
}
