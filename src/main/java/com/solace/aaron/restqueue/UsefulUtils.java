package com.solace.aaron.restqueue;

import com.solacesystems.common.util.ByteArray;
import com.solacesystems.jcsmp.BytesMessage;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.Destination;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.MapMessage;
import com.solacesystems.jcsmp.SDTException;
import com.solacesystems.jcsmp.SDTMap;
import com.solacesystems.jcsmp.SDTStream;
import com.solacesystems.jcsmp.StreamMessage;
import com.solacesystems.jcsmp.TextMessage;
import com.solacesystems.jcsmp.Topic;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.json.JsonStructure;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;

public class UsefulUtils {

    private static final JCSMPFactory f = JCSMPFactory.onlyInstance();
    
    public static TextMessage sdkperfDumpMsgCopy(BytesXMLMessage msg) {
        TextMessage outMsg = f.createMessage(TextMessage.class);
        outMsg.setText(msg.dump());
        return outMsg;
    }

    public static TextMessage jsonMsgCopy(BytesXMLMessage msg) {
        TextMessage outMsg = f.createMessage(TextMessage.class);
        outMsg.setText(jsonMsgCopy2(msg).toString() + "\n");
        return outMsg;
    }

    public static TextMessage jsonPrettyMsgCopy(BytesXMLMessage msg) {
        TextMessage outMsg = f.createMessage(TextMessage.class);
        outMsg.setText(prettyPrint(jsonMsgCopy2(msg)) + "\n");
        return outMsg;
    }

    public static JsonStructure jsonMsgCopy2(BytesXMLMessage msg) {
        JsonObjectBuilder job = Json.createObjectBuilder();
        // topic or queue
        job.add("destination",msg.getDestination().getName());
        job.add("destinationType", msg.getDestination() instanceof Topic ? "Topic" : "Queue");

        // metadata / headers
        if (msg.getApplicationMessageId() != null) {
            job.add("applicationMessageId",msg.getApplicationMessageId());
        }
        if (msg.getApplicationMessageType() != null) job.add("applicationMessageType",msg.getApplicationMessageType());
        if (msg.getConsumerIdList() != null && !msg.getConsumerIdList().isEmpty()) {
            JsonArrayBuilder jab = Json.createArrayBuilder();
            for (Long l : msg.getConsumerIdList()) {
                jab.add(l);
            }
            job.add("consumerIdList", jab.build());
        }
        if (msg.getContentLength() > 0) job.add("contentLength", msg.getContentLength());
        if (msg.getCorrelationId() != null) job.add("correlationId",msg.getCorrelationId());
        job.add("cos", msg.getCos().toString());
        try {
            job.add("deliveryCount", msg.getDeliveryCount());
        } catch (UnsupportedOperationException e) {
            // ignore
        }
        job.add("deliveryMode", msg.getDeliveryMode().toString());
        if (msg.isDMQEligible()) job.add("dmqEligible", msg.isDMQEligible());
        if (msg.getExpiration() > 0) job.add("expiration", msg.getExpiration());
        if (msg.getHTTPContentEncoding() != null) job.add("httpContentEncoding", msg.getHTTPContentEncoding());
        if (msg.getHTTPContentType() != null) job.add("httpContentType", msg.getHTTPContentType());
        if (msg.getMessageId() != null) job.add("mesageId", msg.getMessageId());
        job.add("priority",msg.getPriority());
        if (msg.getRedelivered()) job.add("redelivered", msg.getRedelivered());
        if (msg.isReplyMessage()) job.add("replyMessage", msg.isReplyMessage());
        if (msg.getReplyTo() != null) job.add("replyTo",msg.getReplyTo().getName());
        if (msg.getSenderId() != null) job.add("senderId",msg.getSenderId());
        if (msg.getSenderTimestamp() != null) job.add("senderTimestamp",msg.getSenderTimestamp());
        if (msg.getSequenceNumber() != null) job.add("sequenceNumber",msg.getSequenceNumber());
        if (msg.getTimeToLive() > 0) job.add("timeToLive", msg.getTimeToLive());

        // properties
        if (msg.getProperties() != null) job.add("properties", SDTMapToJson(msg.getProperties()));

        // payload
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
        return job.build();
    }
    
    private static String SDTMapToJson(SDTMap map) {
        JsonObjectBuilder job = Json.createObjectBuilder();
        try {
            for (String key : map.keySet()) {
                Object o = map.get(key);
                if (o instanceof String) {
                    job.add(key, (String)o);
                } else if (o instanceof SDTMap) {
                } else if (o instanceof SDTStream) {
                } else if (o instanceof Double) {
                    job.add(key, (Double)o);
                } else if (o instanceof Float) {
                    job.add(key, (Float)o);
                } else if (o instanceof Integer) {
                    job.add(key, (Integer)o);
                } else if (o instanceof Long) {
                    job.add(key, (Long)o);
                } else if (o instanceof Boolean) {
                    job.add(key, (Boolean)o);
                } else if (o instanceof Short) {
                    job.add(key, (Short)o);
                } else if (o instanceof Byte) {
                    job.add(key, (Byte)o);
                } else if (o instanceof ByteArray) {
                    System.err.println("Cannot convert bytearray: "+map);
                } else if (o instanceof Character) {
                    job.add(key, (Character)o);
                } else if (o instanceof Destination) {
                    job.add(key, ((Destination)o).getName());
                } else {
                    System.err.println("Unhandled type "+o.getClass().getName()+"!!  "+key+", "+o);
                }
            }
            
        } catch (SDTException e) {
            e.printStackTrace();
        }
        return job.build().toString();
    }
    
    public static String prettyPrint(JsonStructure json) {
        return jsonFormat(json, JsonGenerator.PRETTY_PRINTING);
    }

    public static String jsonFormat(JsonStructure json, String... options) {
        StringWriter stringWriter = new StringWriter();
        Map<String, Boolean> config = buildConfig(options);
        JsonWriterFactory writerFactory = Json.createWriterFactory(config);
        JsonWriter jsonWriter = writerFactory.createWriter(stringWriter);

        jsonWriter.write(json);
        jsonWriter.close();

        return stringWriter.toString();
    }
    
    private static Map<String, Boolean> buildConfig(String... options) {
        Map<String, Boolean> config = new HashMap<String, Boolean>();

        if (options != null) {
            for (String option : options) {
                config.put(option, true);
            }
        }

        return config;
    }
    
    
    public static Map<String, List<String>> parseUrlParamQuery(String fullUrl) {
        if (!fullUrl.contains("?")) return Collections.emptyMap();
        String paramStr = fullUrl.split("\\?",2)[1];
        return Arrays.stream(paramStr.split("&"))
                .map(UsefulUtils::splitQueryParameter)
                .collect(Collectors.groupingBy(SimpleImmutableEntry::getKey, LinkedHashMap::new, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }
    
    
    public static SimpleImmutableEntry<String, String> splitQueryParameter(String it) {
        final int idx = it.indexOf("=");
        final String key = idx > 0 ? it.substring(0, idx) : it;
        final String value = idx > 0 && it.length() > idx + 1 ? it.substring(idx + 1) : null;
        try {
            return new SimpleImmutableEntry<>(
                    URLDecoder.decode(key, "UTF-8"),
                    URLDecoder.decode(value, "UTF-8")
            );
        } catch (UnsupportedEncodingException e) {
            return new SimpleImmutableEntry<>(key,value);
        }
    }

    public boolean verifyParmas(Map<String, List<String>> urlParams, Set<String> accepted) {
        return urlParams.keySet().equals(accepted);
    }
        

    
    
    private UsefulUtils() {
        throw new AssertionError("don't instantiate");
    }
}
