package com.solace.aaron.restQ;

public class RestQException extends Exception {
    
    private ReturnValue rv;

    public RestQException() {
        super();
        // TODO Auto-generated constructor stub
    }

    public RestQException(String message, Throwable cause) {
        super(message, cause);
        // TODO Auto-generated constructor stub
    }

    public RestQException(String message) {
        super(message);
        // TODO Auto-generated constructor stub
    }

    public RestQException(Throwable cause) {
        super(cause);
        // TODO Auto-generated constructor stub
    }

    public RestQException(ReturnValue returnValue) {
        super(returnValue.getCause());
        // TODO Auto-generated constructor stub
    }

}
