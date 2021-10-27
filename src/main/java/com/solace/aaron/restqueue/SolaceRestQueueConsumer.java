/*
 * Copyright 2021 Solace Corporation. All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.solace.aaron.restqueue;

import com.solacesystems.jcsmp.Browser;
import com.solacesystems.jcsmp.BytesMessage;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.CapabilityType;
import com.solacesystems.jcsmp.JCSMPChannelProperties;
import com.solacesystems.jcsmp.JCSMPErrorResponseException;
import com.solacesystems.jcsmp.JCSMPErrorResponseSubcodeEx;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.JCSMPTransportException;
import com.solacesystems.jcsmp.Message;
import com.solacesystems.jcsmp.Queue;
import com.solacesystems.jcsmp.SDTException;
import com.solacesystems.jcsmp.SDTMap;
import com.solacesystems.jcsmp.SessionEventArgs;
import com.solacesystems.jcsmp.SessionEventHandler;
import com.solacesystems.jcsmp.TextMessage;
import com.solacesystems.jcsmp.XMLMessageConsumer;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solacesystems.jcsmp.XMLMessageProducer;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SolaceRestQueueConsumer implements XMLMessageListener {

    
    private JCSMPSession session;
    private XMLMessageProducer producer;
    private XMLMessageConsumer consumer;
    private volatile boolean isShutdown = false;             // are we done?

    private static final JCSMPFactory f = JCSMPFactory.onlyInstance();
    private static final Logger logger = LogManager.getLogger();  // log4j2, but could also use SLF4J, JCL, etc.

    
    private Map<String,String> queueToFlowIdMap = new HashMap<>();
    private Map<String,ConsumerFlowDealy> queueToFlowMap = new HashMap<>();
    private Map<String,Map<String,Browser>> browsers = new HashMap<>();

    
    private ReturnValue connectNewQueue(String queueName, String reqCorrId) {
        ConsumerFlowDealy cfd;
        try {
            cfd = ConsumerFlowDealy.connectToQueue(session, queueName, reqCorrId);
            queueToFlowIdMap.put(queueName,cfd.getFlowId());
            queueToFlowMap.put(queueName, cfd);
            return new ReturnValue(200, "OK", true);
/*        } catch (OperationNotSupportedException e) {  // not allowed to do this
            logger.error("Nope, couldn't do that!",e);
            return new ReturnValue(501, e.getMessage(), false);
        } catch (AccessDeniedException e) {
            logger.error("Nope, couldn't do that!",e);
            return new ReturnValue(401, e.getMessage(), false);
        } catch (JCSMPErrorResponseException e) {  // something else went wrong: queue not exist, queue shutdown, etc.
            logger.error("Nope, couldn't do that!",e);
            return new ReturnValue(e.getSubcodeEx(), e.getResponsePhrase(), false);
        } catch (JCSMPException e) {
            logger.error("Nope, couldn't do that!",e);
            return new ReturnValue(501, e.getMessage(), false);
*/
        } catch (Exception e) {
            return handleJcsmpException(e);
        }
    }

    
    private static ReturnValue handleJcsmpException(Exception e) {
        if (e instanceof JCSMPErrorResponseException) {
            JCSMPErrorResponseException e2 = (JCSMPErrorResponseException)e;
            return new ReturnValue(e2.getResponseCode(), e2.getResponsePhrase(), false);
        } else if (e.getCause() != null && e.getCause() instanceof JCSMPErrorResponseException) {
            JCSMPErrorResponseException e2 = (JCSMPErrorResponseException)e.getCause();
            return new ReturnValue(e2.getResponseCode(), e2.getResponsePhrase(), false);
        } else {  // not sure
            return new ReturnValue(500, e.getMessage(), false);
        }
    }
        
    
//    private BytesXMLMessage getFlowMsg(String queueName) {
//        
//    }
//    
//    private boolean ackFlowMsg(String queueName, String ackId) {
//        
//    }
    

/*    private static Map<String,String> parseArgs(String fullUrl) {
        if (!fullUrl.contains("?")) return Collections.emptyMap();
        String paramStr = fullUrl.split("\\?",2)[1]; 
        Map<String,String> retMap = new HashMap<>();
        String[] params = paramStr.split("\\&");
        for (String param : params) {
            String[] pieces = param.split("=");
            retMap.put(pieces[0],pieces[1]);
        }
        return retMap;
    }
*/    
    
        
    
    
    
    void sendErrorResponse(BytesXMLMessage origMsg, ErrorTypes errorType) {
        sendErrorResponse(origMsg, errorType.getCode(), errorType.getMessage());
    }
    
    void sendErrorResponse(BytesXMLMessage origMsg, ReturnValue returnValue) {
        sendErrorResponse(origMsg, returnValue.getHttpReturnCode(), returnValue.getHttpReturnReason());
    }
    
    void sendErrorResponse(BytesXMLMessage origMsg, int code, String reason) {  // reason or reason + message
        TextMessage replyMsg = f.createMessage(TextMessage.class);
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add("reason", reason);
        job.add("code", code);
        replyMsg.setText(job.build().toString() + "\n");
        try {
            SDTMap map = f.createMap();
            map.putShort("JMS_Solace_HTTP_status_code",(short)code);
            map.putString("JMS_Solace_HTTP_reason_phrase",reason);
            replyMsg.setProperties(map);
        } catch (SDTException e) { }  // ignore
        try {
            producer.sendReply(origMsg, replyMsg);
        } catch (JCSMPException e) {
            logger.error("Cannot send an error response message!",e);
        }
    }

    void sendOkResponse(BytesXMLMessage origMsg, String optionalPayload) {
        Message replyMsg;
        if (optionalPayload != null) {
            replyMsg = f.createMessage(TextMessage.class);
            ((TextMessage)replyMsg).setText(optionalPayload+"\n");
        } else {  // empty response
            replyMsg = f.createMessage(BytesMessage.class);
        }
        try {
            SDTMap map = f.createMap();
            map.putShort("JMS_Solace_HTTP_status_code",(short)200);
            map.putString("JMS_Solace_HTTP_reason_phrase","OK");
            replyMsg.setProperties(map);
        } catch (SDTException e) { }  // ignore
        try {
            producer.sendReply(origMsg, replyMsg);
        } catch (JCSMPException e) {
            logger.error("Cannot send a 200 OK response message!",e);
        }
    }


    
    /** This is the main app.  Use this type of app for receiving Guaranteed messages (e.g. via a queue endpoint). */
    public void init(String... args) throws JCSMPException, InterruptedException, IOException {
        if (args.length < 3) {  // Check command line arguments
            System.out.println("Usage: SolaceRestQueueConsumer <host:port> <message-vpn> <client-username> [password]");
            System.exit(-1);
        }
        System.out.println("SolaceRestQueueConsumer initializing...");

        final JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, args[0]);          // host:port
        properties.setProperty(JCSMPProperties.VPN_NAME,  args[1]);     // message-vpn
        properties.setProperty(JCSMPProperties.USERNAME, args[2]);      // client-username
        if (args.length > 3) {
            properties.setProperty(JCSMPProperties.PASSWORD, args[3]);  // client-password
        }
        properties.setProperty(JCSMPProperties.REAPPLY_SUBSCRIPTIONS, true);
        JCSMPChannelProperties channelProps = new JCSMPChannelProperties();
        channelProps.setReconnectRetries(20);      // recommended settings
        channelProps.setConnectRetriesPerHost(5);  // recommended settings
        // https://docs.solace.com/Solace-PubSub-Messaging-APIs/API-Developer-Guide/Configuring-Connection-T.htm
        properties.setProperty(JCSMPProperties.CLIENT_CHANNEL_PROPERTIES, channelProps);
        session = f.createSession(properties, null, new SessionEventHandler() {
            @Override
            public void handleEvent(SessionEventArgs event) {  // could be reconnecting, connection lost, etc.
                System.out.println("### Session event: " + event);
                logger.info("### Session event: " + event);
            }
        });
        session.connect();
        if (!session.isCapable(CapabilityType.BROWSER)) {
            System.out.println("NO BROWSER!");
            System.exit(1);
        }
        if (!session.isCapable(CapabilityType.SUB_FLOW_GUARANTEED)) {
            System.out.println("SUB FLOW GUAHG!");
            System.exit(1);
        }

        producer = session.getMessageProducer(new JCSMPStreamingPublishCorrelatingEventHandler() {
            // unused in Direct Messaging application, only for Guaranteed/Persistent publishing application
            @Override public void responseReceivedEx(Object key) {
            }

            // can be called for ACL violations, connection loss, and Persistent NACKs
            @Override
            public void handleErrorEx(Object key, JCSMPException cause, long timestamp) {
                System.out.printf("### Producer handleErrorEx() callback: %s%n", cause);
                if (cause instanceof JCSMPTransportException) {  // all reconnect attempts failed
                    isShutdown = true;  // let's quit; or, could initiate a new connection attempt
                } else if (cause instanceof JCSMPErrorResponseException) {  // might have some extra info
                    JCSMPErrorResponseException e = (JCSMPErrorResponseException)cause;
                    System.out.println(JCSMPErrorResponseSubcodeEx.getSubcodeAsString(e.getSubcodeEx())
                            + ": " + e.getResponsePhrase());
                    System.out.println(cause);
                }
            }
        });

        
        
        
        consumer = session.getMessageConsumer(this);  // I myself am my own listener (at bottom)
        
        // MicroGateway: Allow: DELETE, GET, HEAD, OPTIONS, PATCH, POST, PUT
        session.addSubscription(f.createTopic("POST/restQ/bind/>"));    // start a consumer flow
        session.addSubscription(f.createTopic("POST/restQ/unbind/>"));  // close a flow
        
        session.addSubscription(f.createTopic("GET/restQ/con/>"));      // consume a msg off a flowId
        session.addSubscription(f.createTopic("DELETE/restQ/ack/>"));   // ack a msg off a flowId

        session.addSubscription(f.createTopic("GET/restQ/browse/>"));   // start a read-only browse session
        session.addSubscription(f.createTopic("POST/restQ/browse/>"));  // start a read/delete browse session
        session.addSubscription(f.createTopic("GET/restQ/next/>"));     // get next msg off a browseId
        session.addSubscription(f.createTopic("DELETE/restQ/del/>"));   // delete a msg off a browseId

        session.addSubscription(f.createTopic("GET/restQ/getMsg/>"));        // get a specific msg by flowId and msgId
        session.addSubscription(f.createTopic("GET/restQ/unacked/>"));    // get a list of unacked msgs based on flowId

        session.addSubscription(f.createTopic("HEAD/restQ/keepalive/>"));  // heartbeat to keep flow or browse alive
        
        session.addSubscription(f.createTopic("*/restQ/>"));            // suck it!  not interested

        consumer.start();
    }

    public static void main(String... args) throws JCSMPException, InterruptedException, IOException {
        SolaceRestQueueConsumer srqc = new SolaceRestQueueConsumer();
        srqc.init(args);
        
        // async queue receive working now, so time to wait until done...
        System.out.println("SolaceRestQueueConsumer connected, and running. Press [ENTER] to quit.");
        while (System.in.available() == 0 && !srqc.isShutdown) {
            Thread.sleep(1000);  // wait 1 second
        
        }
        srqc.isShutdown = true;
        //flowQueueReceiver.stop();
        Thread.sleep(1000);
        srqc.session.closeSession();  // will also close consumer object
        System.out.println("Main thread quitting.");
    }
    
    ////////////////////////////////////////////

    
    private void getMessage(RequestMessageObject rmo) {
        String flowId = "";
        String msgId = "";
        String format = "json";
        if (rmo.urlParams.get("flowId") != null) flowId = rmo.urlParams.get("flowId").get(0);
        if (rmo.urlParams.get("msgId") != null) msgId = rmo.urlParams.get("msgId").get(0);
        if (rmo.urlParams.get("format") != null) format = rmo.urlParams.get("format").get(0);
        if (!queueToFlowMap.containsKey(rmo.queueName) || !queueToFlowIdMap.get(rmo.queueName).equals(flowId)) {
            sendErrorResponse(rmo.requestMessage, ErrorTypes.INVALID_FLOW_ID);
            return;
        }
        if (queueToFlowMap.get(rmo.queueName).unackedMessages.get(msgId) == null) {
            sendErrorResponse(rmo.requestMessage, ErrorTypes.INVALID_MSG_ID);
            return;
        }
        BytesXMLMessage msg = queueToFlowMap.get(rmo.queueName).unackedMessages.get(msgId);
        try {
            producer.sendReply(rmo.requestMessage, 
                    format.equals("dump") ? UsefulUtils.sdkperfDumpMsgCopy(msg) : UsefulUtils.jsonMsgCopy(msg));
        } catch (JCSMPException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void getUnacked(RequestMessageObject rmo) {
        String flowId = "";
        if (rmo.urlParams.get("flowId") != null) flowId = rmo.urlParams.get("flowId").get(0);
        if (!queueToFlowMap.containsKey(rmo.queueName) || !queueToFlowIdMap.get(rmo.queueName).equals(flowId)) {
            sendErrorResponse(rmo.requestMessage, ErrorTypes.INVALID_FLOW_ID);
            return;
        }
        JsonObjectBuilder job = Json.createObjectBuilder();
        JsonArrayBuilder jab = Json.createArrayBuilder();
        for (String msgId : queueToFlowMap.get(rmo.queueName).unackedMessages.keySet()) {
            jab.add(msgId);
        }
        job.add("msgId", jab);
        sendOkResponse(rmo.requestMessage, job.build().toString());
    }
    
    private class RequestMessageObject {
        
        final String queueName;
        final BytesXMLMessage requestMessage;
        final String requestCorrelationId;
        final Map<String, List<String>> urlParams;
        
        private RequestMessageObject(String queueName, BytesXMLMessage requestMessage, String requestCorrelationId,                 Map<String, List<String>> urlParams) {
            this.queueName = queueName;
            this.requestMessage = requestMessage;
            this.requestCorrelationId = requestCorrelationId;
            this.urlParams = Collections.unmodifiableMap(urlParams);
        }
    }
    
    
    
    @Override
    public void onReceive(BytesXMLMessage requestMessage) {
        //System.out.println(requestMessage.dump());
        if (requestMessage.getDestination() instanceof Queue) return;  // IGNORE! impossible!  lol
        String topic = requestMessage.getDestination().getName();
        String queueName = topic.split("/",4)[3];  // will always work due to subscription, at least 3 levels
        // we're assuming that this app is using MicroGateway mode, which auto-populates correlation ID & some User Properties
        String requestCorrelationId = requestMessage.getCorrelationId();
        if (requestCorrelationId == null) {
            sendErrorResponse(requestMessage, 400, "no message Correlation ID present, use microgateway");
            return;
        }
        if (requestMessage.getProperties() == null) {
            sendErrorResponse(requestMessage, 400, "missing user properties, no map, use microgateway");
            return;
        }
        Map<String, List<String>> urlParams = Collections.emptyMap();
//        String flowId = null;
//        String msgId = null;
//        String format = "json";  // default
        
        try {
            String fullUrl = requestMessage.getProperties().getString("JMS_Solace_HTTP_target_path_query_verbatim");
            if (fullUrl == null) {
                sendErrorResponse(requestMessage, 400, "missing user property \"JMS_Solace_HTTP_target_path_query_verbatim\", use microgateway");
                return;
            }
            try {
                urlParams = UsefulUtils.parseUrlParamQuery(fullUrl);
            } catch (RuntimeException e) {
                sendErrorResponse(requestMessage, 400, "invalid URL parameter syntax");
                return;
            }
            System.out.println("Parsed URL params: " + urlParams);
        } catch (SDTException e) {  // this really shouldn't happen!
            sendErrorResponse(requestMessage, handleJcsmpException(e));
            e.printStackTrace();
            logger.error(e);
            assert false;
            return;
        }
        // now we start the topic demuxing process!!
        
        /////////////////////////////////////
        // BIND TO A QUEUE
        if (topic.startsWith("POST/restQ/bind/")) {
            if (!urlParams.isEmpty()) {
                sendErrorResponse(requestMessage, ErrorTypes.URL_PARAMS_NOT_EMPTY);
                return;
            }
            if (queueToFlowIdMap.containsKey(queueName)) {
                sendErrorResponse(requestMessage, 400, "queue " + queueName + " already has active flow");
                return;
            }
            ReturnValue rv = connectNewQueue(queueName,requestCorrelationId);
            if (rv.isSuccess()) {
                sendOkResponse(requestMessage, "{flowId=\"" + queueToFlowMap.get(queueName).getFlowId() + "\"}");
                return;
            } else {
                sendErrorResponse(requestMessage, rv);
                return;
            }
        }
        
        ////////////////////////////
        // CONSUME!
        else if (topic.startsWith("GET/restQ/con/")) {
            String flowId = "";
            String format = "";
            if (urlParams.get("flowId") != null) flowId = urlParams.get("flowId").get(0);
            if (urlParams.get("format") != null) format = urlParams.get("format").get(0);  // optional
            if (!queueToFlowMap.containsKey(queueName)) {
                sendErrorResponse(requestMessage, 404, "queue consumer not active");
                return;
            } else if (!queueToFlowIdMap.get(queueName).equals(flowId)) {
                sendErrorResponse(requestMessage, ErrorTypes.INVALID_FLOW_ID);
                return;
            }
            try {
                BytesXMLMessage msg = queueToFlowMap.get(queueName).getNextMessage(requestCorrelationId);
                if (msg == null) {
                    sendErrorResponse(requestMessage, 404, "no messages");
                    return;
                } else {
                    if (format.equals("dump")) {
                        producer.sendReply(requestMessage, UsefulUtils.sdkperfDumpMsgCopy(msg));
                    } else {
                        producer.sendReply(requestMessage, UsefulUtils.jsonMsgCopy(msg));
                    }
                    System.out.println(queueToFlowMap.get(queueName).unackedMessages.keySet());
                    /////////////////////////////////////////////////
                    //msg.ackMessage();
                    /////////////////////////////////////////////////
                }
            } catch (JCSMPException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                sendErrorResponse(requestMessage, handleJcsmpException(e));
            }
        }

        //////////////////////////
        // ACK consumed message
        else if (topic.startsWith("DELETE/restQ/ack/")) {  // ACKing a message!
            if (!queueToFlowMap.containsKey(queueName)) {
                sendErrorResponse(requestMessage, 404, "queue consumer not active");
                return;
            }
            String ackId = null;
            if (urlParams.get("ackId") != null) ackId = urlParams.get("ackId").get(0);
            if (ackId == null) {
                sendErrorResponse(requestMessage, 400, "missing URL param 'ackId' for ACK correlation");
                return;
            }
            if (queueToFlowMap.get(queueName).unackedMessages.get(ackId) == null) {
                sendErrorResponse(requestMessage, 404, "unknown ackId '"+ackId+"' for queue "+queueName);
                return;
            }
            // else, good to go!
            queueToFlowMap.get(queueName).unackedMessages.get(ackId).ackMessage();
            System.out.println("Successfully ACKed "+queueToFlowMap.get(queueName).unackedMessages.get(ackId));
            queueToFlowMap.get(queueName).unackedMessages.remove(ackId);
            sendOkResponse(requestMessage,"");
        }
            
            
            

        else if (topic.startsWith("GET/queueBro/")) {  // browser!
            String flowId = "";
            if (flowId.isEmpty()) {
                sendErrorResponse(requestMessage, 400, "no unique \"id\" URL param included");
                return;
            }
            if (!browsers.containsKey(queueName)) {
//                ReturnValue result = connectNewBrowse(parsedQueueName);
//                if (!result.isSuccess()) {
//                    // send error response
//                    sendErrorResponse(requestMessage, result);
//                    return;
//
//                }

            }
                        
        }
        
        ////////////////////////////
        // GET MESSAGE!
        else if (topic.startsWith("GET/restQ/getMsg/")) {
            RequestMessageObject rmo = new RequestMessageObject(queueName, requestMessage, requestCorrelationId, urlParams);
            getMessage(rmo);
        }

        ////////////////////////////
        // LIST UNACKED MESSAGES!
        else if (topic.startsWith("GET/restQ/unacked/")) {
            RequestMessageObject rmo = new RequestMessageObject(queueName, requestMessage, requestCorrelationId, urlParams);
            getUnacked(rmo);
        }
        
        else {
            System.err.println("RECEIVED A MESSAGE THAT WE DON'T SUBSCRIBE TO!!!");
            System.err.println(requestCorrelationId);
            System.err.println(UsefulUtils.jsonMsgCopy(requestMessage).getText());
        }

    }

    @Override
    public void onException(JCSMPException e) {  // uh oh!
        System.out.printf("### MessageListener's Direct onException(): %s%n",e);
        e.printStackTrace();
        if (e instanceof JCSMPTransportException) {  // all reconnect attempts failed
            isShutdown = true;  // let's quit; or, could initiate a new connection attempt
        }
    
    }

}
