package com.solace.aaron.restqueue;

import com.solacesystems.jcsmp.BytesMessage;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.MapMessage;
import com.solacesystems.jcsmp.StreamMessage;
import com.solacesystems.jcsmp.TextMessage;
import com.solacesystems.jcsmp.Topic;
import java.util.Base64;
import javax.json.Json;
import javax.json.JsonObjectBuilder;

public class MessageUtils {

    
    
/*    public static BytesXMLMessage copyMessage(BytesXMLMessage msg) {
        BytesXMLMessage outMsg = JCSMPFactory.onlyInstance().createMessage(BytesXMLMessage.class);
        if (msg.getApplicationMessageId() != null) {
            outMsg.setApplicationMessageId(msg.getApplicationMessageId());
        }
        if (msg.getApplicationMessageType() != null) outMsg.setApplicationMessageType(msg.getApplicationMessageType());
        if (msg.getCorrelationId() != null) outMsg.setCorrelationId(msg.getCorrelationId());
        if (msg.getSequenceNumber() != null) outMsg.setSequenceNumber(msg.getSequenceNumber());
        if (msg.getSenderId() != null) outMsg.setSenderId(msg.getSenderId());
        if (msg.getSenderTimestamp() != null) outMsg.setSenderTimestamp(msg.getSenderTimestamp());
        outMsg.setPriority(msg.getPriority());
        if (msg.getReplyTo() != null) outMsg.setReplyTo(msg.getReplyTo());
        
        outMsg.writeAttachment(msg.dump().getBytes());//msg.getAttachmentByteBuffer().array());

        try {
            SDTMap map = JCSMPFactory.onlyInstance().createMap();
            map.putShort("JMS_Solace_HTTP_status_code",(short)200);
            if (outMsg.getApplicationMessageId() != null) map.putString("JMS_Solace_HTTP_Message_ID",outMsg.getApplicationMessageId());
            outMsg.setProperties(map);
        } catch (SDTException e) { }
        return outMsg;
    }
*/    
    
    public static TextMessage sdkperfDumpMsgCopy(BytesXMLMessage msg) {
        TextMessage outMsg = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
        outMsg.setText(msg.dump());
        return outMsg;
    }
    

    
    public static TextMessage jsonMsgCopy(BytesXMLMessage msg) {
        
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
            job.add("payload", ((TextMessage)msg).getText());
            job.add("messageClass", "TextMessage");
        } else if (msg instanceof BytesMessage) {
            job.add("payload", new String(Base64.getEncoder().encode(msg.getAttachmentByteBuffer().array())));
            job.add("messageClass", "BytesMessage");
        } else if (msg instanceof MapMessage) {
            job.add("payload", new String(Base64.getEncoder().encode(msg.getAttachmentByteBuffer().array())));
            job.add("messageClass", "MapMessage");
        } else if (msg instanceof StreamMessage) {
            job.add("payload", new String(Base64.getEncoder().encode(msg.getAttachmentByteBuffer().array())));
            job.add("messageClass", "StreamMessage");
        } else {
            job.add("payload", new String(Base64.getEncoder().encode(msg.getAttachmentByteBuffer().array())));
            job.add("messageClass", msg.getClass().getName());
        }
//        JsonWriter jw = Json.
        TextMessage outMsg = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
        outMsg.setText(job.build().toString());
        return outMsg;
    }
    

    
    
    private MessageUtils() {
        throw new AssertionError("don't instantiate");
    }
}
