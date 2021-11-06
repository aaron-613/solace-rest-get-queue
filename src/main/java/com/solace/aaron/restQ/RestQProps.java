package com.solace.aaron.restQ;

import java.util.Properties;

public class RestQProps extends Properties {

    private static final long serialVersionUID = 1L;

    public enum Props {
        BIND_URL("POST/restQ/bind"),
        ;
        
        Props(String url) {
            
        }
    }
    
    
    
}
