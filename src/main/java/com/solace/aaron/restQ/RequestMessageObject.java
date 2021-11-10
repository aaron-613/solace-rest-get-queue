package com.solace.aaron.restQ;

import com.solacesystems.jcsmp.BytesMessage;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.TextMessage;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Convenience/helper class to store a representation of a received JCSMP Message via the REST
 * interface.  
 * @author AaronLee
 *
 */
class RequestMessageObject {
        
    final String resourceName;
    final BytesXMLMessage requestMessage;
    final String uuid = UUID.randomUUID().toString();
    final Map<String, List<String>> requestParams;
    final String payloadString;
    
    RequestMessageObject(String resourceName, BytesXMLMessage requestMessage, Map<String, List<String>> requestParams) {
        this.resourceName = resourceName;
        this.requestMessage = requestMessage;
        if (requestParams != null) this.requestParams = Collections.unmodifiableMap(requestParams);
        else this.requestParams= Collections.unmodifiableMap(Collections.emptyMap());
        this.payloadString = parsePayload(requestMessage);
    }
    
    private String parsePayload(BytesXMLMessage msg) {
        if (msg instanceof TextMessage) {
            return ((TextMessage)msg).getText();
        } else if (msg instanceof BytesMessage) {
            try {
                return new String(((BytesMessage)msg).getData(),Charset.forName("UTF-8"));
            } catch (RuntimeException e) {
                return "";
            }
        } else {
            // map or stream message?
            return "";
        }
    }
    
    String getParam(String key) {
        if (requestParams.containsKey(key)) return requestParams.get(key).get(0);
        else return null;
    }
    
    String getPayloadString() {
        return payloadString;
    }

    /**
     * Pass in a set of mandatory keys, verify they've all been passed in and just one of each.
     * Will also verify for any other (optional) keys present, there is only one of each.
     */
    boolean checkForMandatoryParams(String... keys) {
        Set<String> mandatoryKeySet = new HashSet<>(Arrays.asList(keys));
        for (String key : mandatoryKeySet) {                       // for each mandatory key
            if (!requestParams.containsKey(key)) return false;     // does my passed params contain this key?
            if (requestParams.get(key).size() != 1) return false;  // and occurs only once?
        }
        return true;
    }

    
    /**
     * Pass in a set of keys, verify they've all been passed in and just one of each.
     * Will also verify for any other (optional) keys present, there is only one of each.
     */
    boolean checkForAllowedParams(String... keys) {
        Set<String> allowedKeySet = new HashSet<>(Arrays.asList(keys));
        // first, make sure all mandatory keys are in my URL params
        // next, make each URL param appears only once
        for (String key : requestParams.keySet()) {                // for each passed param key
            if (!allowedKeySet.contains(key)) return false;        // is it allowed?
            if (requestParams.get(key).size() != 1) return false;  // and occurs only once?
        }
        return true;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("resourceName:  %s%n",resourceName));
        sb.append(String.format("uuid:          %s%n",uuid));
        sb.append(String.format("requestParams: %s%n",requestParams.toString()));
        sb.append(String.format("payload:       %s%n",payloadString));
        //sb.append(requestMessage.dump());
        return sb.toString();
    }
}
