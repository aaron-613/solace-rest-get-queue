package com.solace.aaron.restqueue;

import com.solacesystems.jcsmp.BytesMessage;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.MapMessage;
import com.solacesystems.jcsmp.StreamMessage;
import com.solacesystems.jcsmp.TextMessage;
import com.solacesystems.jcsmp.Topic;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonObjectBuilder;

public class UsefulUtils {

    private static final JCSMPFactory f = JCSMPFactory.onlyInstance();
    
    public static TextMessage sdkperfDumpMsgCopy(BytesXMLMessage msg) {
        TextMessage outMsg = f.createMessage(TextMessage.class);
        outMsg.setText(msg.dump());
        return outMsg;
    }

    public static TextMessage jsonMsgCopy(BytesXMLMessage msg) {
        TextMessage outMsg = f.createMessage(TextMessage.class);
        outMsg.setText(jsonMsgCopy2(msg));
        return outMsg;
    }

    public static String jsonMsgCopy2(BytesXMLMessage msg) {
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add("destination",msg.getDestination().getName());
        //job.add("destinationType",msg.getDestination().getClass().getSimpleName());
        job.add("destinationType", msg.getDestination() instanceof Topic ? "Topic" : "Queue");
        if (msg.getApplicationMessageId() != null) {
            job.add("applicationMessageId",msg.getApplicationMessageId());
        }
        if (msg.getApplicationMessageType() != null) job.add("applicationMessageType",msg.getApplicationMessageType());
        if (msg.getCorrelationId() != null) job.add("correlationId",msg.getCorrelationId());
        if (msg.getSequenceNumber() != null) job.add("sequenceNumber",msg.getSequenceNumber());
        if (msg.getSenderId() != null) job.add("senderId",msg.getSenderId());
        if (msg.getSenderTimestamp() != null) job.add("senderTimestamp",msg.getSenderTimestamp());
        job.add("priority",msg.getPriority());
        job.add("cos", msg.getCos().toString());
        job.add("deliveryMode", msg.getDeliveryMode().toString());
        if (msg.getReplyTo() != null) job.add("replyTo",msg.getReplyTo().getName());
        if (msg.isReplyMessage()) job.add("replyMessage", msg.isReplyMessage());
        if (msg.isDMQEligible()) job.add("dmqEligible", msg.isDMQEligible());
        if (msg.getRedelivered()) job.add("redelivered", msg.getRedelivered());
        //if (msg.getMessageId() != null) job.add("mesageId", msg.getMessageId());
        try {
            job.add("deliveryCount", msg.getDeliveryCount());
        } catch (UnsupportedOperationException e) {
            // ignore
        }
        if (msg.getExpiration() > 0) job.add("expiration", msg.getExpiration());
        if (msg.getHTTPContentEncoding() != null) job.add("httpContentEncoding", msg.getHTTPContentEncoding());
        if (msg.getHTTPContentType() != null) job.add("httpContentType", msg.getHTTPContentType());
        
        if (msg instanceof TextMessage) {
            job.add("messageClass", "TextMessage");
            job.add("payload", ((TextMessage)msg).getText());
        } else if (msg instanceof BytesMessage) {
            job.add("messageClass", "BytesMessage");
            job.add("payload", new String(Base64.getEncoder().encode(msg.getAttachmentByteBuffer().array())));
        } else if (msg instanceof MapMessage) {
            job.add("messageClass", "MapMessage");
            job.add("payload", new String(Base64.getEncoder().encode(msg.getAttachmentByteBuffer().array())));
        } else if (msg instanceof StreamMessage) {
            job.add("messageClass", "StreamMessage");
            job.add("payload", new String(Base64.getEncoder().encode(msg.getAttachmentByteBuffer().array())));
        } else {
            job.add("messageClass", msg.getClass().getName());
            job.add("payload", new String(Base64.getEncoder().encode(msg.getAttachmentByteBuffer().array())));
        }
        return job.build().toString() + "\n";
    }
    
    public static Map<String, List<String>> parseUrlParamQuery(String fullUrl) {
        if (!fullUrl.contains("?")) return Collections.emptyMap();
        String paramStr = fullUrl.split("\\?",2)[1];
        return Arrays.stream(paramStr.split("&"))
                .map(UsefulUtils::splitQueryParameter)
                .collect(Collectors.groupingBy(SimpleImmutableEntry::getKey, LinkedHashMap::new, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }
    
    private static final Charset UTF8 = Charset.forName("UTF-8");

    public static SimpleImmutableEntry<String, String> splitQueryParameter(String it) {
        final int idx = it.indexOf("=");
        final String key = idx > 0 ? it.substring(0, idx) : it;
        final String value = idx > 0 && it.length() > idx + 1 ? it.substring(idx + 1) : null;
        return new SimpleImmutableEntry<>(
                key,value
//            URLDecoder.decode(key, UTF8),
//            URLDecoder.decode(value, UTF8)
        );
    }

    public boolean verifyParmas(Map<String, List<String>> urlParams, Set<String> accepted) {
        return urlParams.keySet().equals(accepted);
    }
        

    
    
    private UsefulUtils() {
        throw new AssertionError("don't instantiate");
    }
}
