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

class RequestMessageObject {
        
    final String resourceName;
    final BytesXMLMessage requestMessage;
    final String requestCorrelationId;
    final Map<String, List<String>> urlParams;
    final String payload;
    
    RequestMessageObject(String resourceName, BytesXMLMessage requestMessage, String requestCorrelationId, Map<String, List<String>> urlParams) {
        this.resourceName = resourceName;
        this.requestMessage = requestMessage;
        this.requestCorrelationId = requestCorrelationId;
        if (urlParams != null) this.urlParams = Collections.unmodifiableMap(urlParams);
        else this.urlParams= Collections.unmodifiableMap(Collections.emptyMap());
        this.payload = parsePayload(requestMessage);
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
        if (urlParams.containsKey(key)) return urlParams.get(key).get(0);
        else return null;
    }
    
    String getPayload() {
        return payload;
    }
    
    /** pass in a set of keys, verify they've all been passed in and just one of each. */
    boolean checkForAllowedParams(String... keys) {
        Set<String> keySet = new HashSet<>(Arrays.asList(keys));
        // first, make sure all mandatory keys are in my URL params
        // next, make each URL param appears only once
        for (String key : urlParams.keySet()) {
            if (!keySet.contains(key)) return false;
            if (urlParams.get(key).size() != 1) return false;
        }
        return true;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("resourceName:         %s%n",resourceName));
        sb.append(String.format("requestCorrelationId: %s%n",requestCorrelationId));
        sb.append(String.format("urlParams:            %s%n",urlParams.toString()));
        sb.append(String.format("payload:              %s%n",payload));
        sb.append(requestMessage.dump());
        return sb.toString();
    }
}
