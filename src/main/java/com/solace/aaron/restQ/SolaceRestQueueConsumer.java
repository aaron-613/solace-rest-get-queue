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

package com.solace.aaron.restQ;

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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SolaceRestQueueConsumer implements XMLMessageListener {


    public enum Mode {
        GATEWAY,
        MESSAGING,
        ;
    }
    
    private Mode mode = Mode.GATEWAY;
    
    private JCSMPSession session;
    private XMLMessageProducer producer;
    private XMLMessageConsumer consumer;
    private volatile boolean isShutdown = false;             // are we done?

    private static final JCSMPFactory f = JCSMPFactory.onlyInstance();
    private static final Logger logger = LogManager.getLogger();  // log4j2, but could also use SLF4J, JCL, etc.

    
    private FlowManager flowManager = new ConsumerFlowManager();
    
    //private Map<String,Map<String,Browser>> browsers = new HashMap<>();

    public static final String QUEUE_SUB_MATCH_PATTERN = ">";  // all queues
//    public static final String CORR_ID_REGEX = "ID:Solace\\-[0-9a-f]{16}"; 
    public static final String CORR_ID_REGEX = "([0-9a-f]{16})";

    
    
    
    
    
    private ReturnValue connectNewQueue(RequestMessageObject rmo) { //  queueName, String reqCorrId) {
        //Flow cfd;
        
        try {
            String flowId = flowManager.connectToQueue(session, rmo);
            // so that was successful, so now add subs to that flow
            //String flowId = flowManager.getFlowId(queueName);
            session.addSubscription(f.createTopic("GET/restQ/*/"+flowId),true);         // catch-all for this flowId
//            session.addSubscription(f.createTopic("GET/restQ/rec/"+flowId),true);         // consume a msg off a flowId
//            session.addSubscription(f.createTopic("DELETE/restQ/ack/"+flowId),true);      // ack a msg off a flowId
//            session.addSubscription(f.createTopic("GET/restQ/getMsg/"+flowId),true);      // get a specific msg by flowId and msgId
//            session.addSubscription(f.createTopic("GET/restQ/unacked/"+flowId),true);     // get a list of unacked msgs based on flowId
//            session.addSubscription(f.createTopic("HEAD/restQ/keepalive/"+flowId),true);  // heartbeat to keep flow or browse alive
            return new ReturnValue(201, "OK", true)
//                    .withHttpHeader("JMS_Solace_HTTP_field_Location","/restQ/rec/"+flowId)  // can't pass this through
                    ;
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
            return UsefulUtils.handleJcsmpException(e);
        }
    }

    
    
    
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
        replyMsg.setHTTPContentType("application/json");
        try {
            SDTMap map = f.createMap();
            map.putShort("JMS_Solace_HTTP_status_code",(short)code);
            map.putString("JMS_Solace_HTTP_reason_phrase",reason);
            replyMsg.setProperties(map);
        } catch (SDTException e) { }  // ignore
        try {
            producer.sendReply(origMsg, replyMsg);
            System.out.printf("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<%nRESPONSE MESSAGE:%n");
            System.out.println(replyMsg.dump());
        } catch (JCSMPException e) {
            logger.error("Cannot send an error response message!",e);
        }
    }

    void sendOkResponse(BytesXMLMessage origMsg) {
        sendOkResponse(origMsg, "", 200, Collections.emptyMap());
    }

    void sendOkResponse(BytesXMLMessage origMsg, String payload) {
        sendOkResponse(origMsg, payload, 200, Collections.emptyMap());
    }

    void sendOkResponse(BytesXMLMessage origMsg, String payload, int code, Map<String,String> otherHeaders) {
        TextMessage replyMsg = f.createMessage(TextMessage.class);
        if (payload != null && !payload.isEmpty()) {
            replyMsg.setText(payload+"\n");
            replyMsg.setHTTPContentType("application/json");
        } else {  // empty response
            replyMsg.setText("");
            replyMsg.setHTTPContentType("text/plain");
        }
        try {
            SDTMap map = f.createMap();
            map.putShort("JMS_Solace_HTTP_status_code",(short)code);
            map.putString("JMS_Solace_HTTP_reason_phrase","OK");
            map.putString("JMS_Solace_HTTP_location","/test/blah");
            for (String key : otherHeaders.keySet()) {
                map.putString(key, otherHeaders.get(key));
            }
            replyMsg.setProperties(map);
        } catch (SDTException e) { }  // ignore
        try {
            producer.sendReply(origMsg, replyMsg);
            System.out.printf("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<%nRESPONSE MESSAGE:%n");
            System.out.println(replyMsg.dump());
        } catch (JCSMPException e) {
            logger.error("Cannot send a 200 OK response message!",e);
        }
    }


    
    /** This is the main app.  Use this type of app for receiving Guaranteed messages (e.g. via a queue endpoint). */
    public void init(String... args) throws JCSMPException, InterruptedException, IOException {
        if (args.length < 3) {  // Check command line arguments
            System.out.println("Usage: SolaceRestQueueConsumer <host:port> <message-vpn> <client-username> [password]");
            System.exit(1);
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
        String cName = (String)session.getProperty(JCSMPProperties.CLIENT_NAME);
        session.setProperty(JCSMPProperties.CLIENT_NAME, "restQ_"+cName);
        session.setProperty(JCSMPProperties.APPLICATION_DESCRIPTION , "restQ app");
        session.connect();
        if (!session.isCapable(CapabilityType.BROWSER)) {
            System.out.println("NO BROWSER CAPABILITY!");
            System.exit(2);
        }
        if (!session.isCapable(CapabilityType.SUB_FLOW_GUARANTEED)) {
            System.out.println("SUB FLOW GUAHG cannot have!");
            System.out.println("- ensure your client-profile allows \"guaranteed receive\"");
            System.exit(3);
        }

        producer = session.getMessageProducer(new JCSMPStreamingPublishCorrelatingEventHandler() {
            // unused in Direct Messaging application, only for Guaranteed/Persistent publishing application
            @Override public void responseReceivedEx(Object key) {
            }

            // can be called for ACL violations, connection loss, and Persistent NACKs
            @Override
            public void handleErrorEx(Object key, JCSMPException cause, long timestamp) {
                System.out.printf("### Producer handleErrorEx() callback: %s%n", cause);
                if (cause instanceof JCSMPErrorResponseException) {  // might have some extra info
                    JCSMPErrorResponseException e = (JCSMPErrorResponseException)cause;
                    System.out.println(JCSMPErrorResponseSubcodeEx.getSubcodeAsString(e.getSubcodeEx())
                            + ": " + e.getResponsePhrase());
                    System.out.println(cause);
                }
            }
        });

        
        
        
        consumer = session.getMessageConsumer(this);  // I myself am my own listener (at bottom)
        
        // MicroGateway: Allow: DELETE, GET, HEAD, OPTIONS, PATCH, POST, PUT
        session.addSubscription(f.createTopic("POST/restQ/bind/"+QUEUE_SUB_MATCH_PATTERN));    // start a consumer flow
        //session.addSubscription(f.createTopic("POST/restQ/unbind/"+QUEUE_SUB_MATCH_PATTERN));  // close a flow
        
        session.addSubscription(f.createTopic("GET/restQ/browse/"+QUEUE_SUB_MATCH_PATTERN));   // start a read-only browse session
        session.addSubscription(f.createTopic("POST/restQ/browse/"+QUEUE_SUB_MATCH_PATTERN));  // start a read/delete browse session
        
        // these next ones will us a flow
//        session.addSubscription(f.createTopic("GET/restQ/rec/"+QUEUE_SUB_MATCH_PATTERN));      // consume a msg off a flowId
//        session.addSubscription(f.createTopic("DELETE/restQ/ack/"+QUEUE_SUB_MATCH_PATTERN));   // ack a msg off a flowId

//        session.addSubscription(f.createTopic("GET/restQ/next/"+QUEUE_SUB_MATCH_PATTERN));     // get next msg off a browseId
//        session.addSubscription(f.createTopic("DELETE/restQ/del/"+QUEUE_SUB_MATCH_PATTERN));   // delete a msg off a browseId

//        session.addSubscription(f.createTopic("GET/restQ/getMsg/"+QUEUE_SUB_MATCH_PATTERN));        // get a specific msg by flowId and msgId
//        session.addSubscription(f.createTopic("GET/restQ/unacked/"+QUEUE_SUB_MATCH_PATTERN));    // get a list of unacked msgs based on flowId
//
//        session.addSubscription(f.createTopic("HEAD/restQ/keepalive/"+QUEUE_SUB_MATCH_PATTERN));  // heartbeat to keep flow or browse alive
        
//        session.addSubscription(f.createTopic("*/restQ/>"));            // suck it!  not interested

        consumer.start();
    }
    
    void shutdown() {
        isShutdown = true;
        flowManager.shutdown();
    }

    public static void main(String... args) throws JCSMPException, InterruptedException, IOException {
        
        
        SolaceRestQueueConsumer srqc = new SolaceRestQueueConsumer();
        srqc.init(args);
        
        // async queue receive working now, so time to wait until done...
        System.out.println("SolaceRestQueueConsumer connected, and running. Press [ENTER] to quit.");
        while (System.in.available() == 0 && !srqc.isShutdown) {
            Thread.sleep(100);  // wait 1 second
        
        }
        System.out.println("Shut down detected...");
        srqc.shutdown();
        Thread.sleep(1000);
        srqc.shutdown();
        srqc.session.closeSession();  // will also close consumer object
        System.out.println("Main thread quitting.");
    }
    
    ////////////////////////////////////////////

    

    
    private void bindToQueue(RequestMessageObject rmo) {
        // param check
        if (!rmo.urlParams.isEmpty()) {
            sendErrorResponse(rmo.requestMessage, ErrorTypes.URL_PARAMS_NOT_EMPTY);
            return;
        }
        if (flowManager.hasActiveFlow(rmo.resourceName)) {
            sendErrorResponse(rmo.requestMessage, 400, "queue " + rmo.resourceName + " already has active flow");
            return;
        }
        
        // ok, let's try to connect...
        ReturnValue rv = connectNewQueue(rmo);
        if (rv.isSuccess()) {
            JsonObjectBuilder job = Json.createObjectBuilder();
            job.add("flowId", flowManager.getFlowId(rmo.resourceName));
            sendOkResponse(rmo.requestMessage, job.build().toString(), rv.getHttpReturnCode(), rv.getHttpHeaders());
            return;
        } else {
            sendErrorResponse(rmo.requestMessage, rv);
            return;
        }
    }
    
    private void unbindFromQueue(RequestMessageObject rmo) {
        // param check
        if (!rmo.checkForAllowedParams("flowId")) {
            sendErrorResponse(rmo.requestMessage, ErrorTypes.INVALID_URL_PARAMS);
            return;
        }
        Flow flow = flowManager.getFlowFromId(rmo.getParam("flowId"));  // get the Flow with this id
        if (!flow.getQueueName().equals(rmo.resourceName)) {  // verify it matches the queue in the URL
            sendErrorResponse(rmo.requestMessage, ErrorTypes.INVALID_FLOW_ID);
            return;
        }
        // now double-check that the flow that we think is bound to this queue is the right one
        if (!flowManager.getFlowId(rmo.resourceName).equals(rmo.getParam("flowId"))) {
            sendErrorResponse(rmo.requestMessage, ErrorTypes.INVALID_FLOW_ID);
//            sendErrorResponse(rmo.requestMessage, 400, "queue " + rmo.resourceName + " already has active flow");
            return;
        }
        try {
            flowManager.unbind(rmo.resourceName,rmo.getParam("flowId"));
            System.out.println("Successful unbind from "+rmo.getParam("flowId"));
            sendOkResponse(rmo.requestMessage,null);
        } catch (RuntimeException e) {
            logger.error("Caught while trying to unbind flow {} on queue {}",rmo.getParam("flowId"),rmo.resourceName);
            sendErrorResponse(rmo.requestMessage, UsefulUtils.handleJcsmpException(e));
        }
        
        // ok, let's try to connect...
        ReturnValue rv = connectNewQueue(rmo);
        if (rv.isSuccess()) {
            JsonObjectBuilder job = Json.createObjectBuilder();
            job.add("flowId", flowManager.getFlowId(rmo.resourceName));
            sendOkResponse(rmo.requestMessage, job.build().toString(), rv.getHttpReturnCode(), rv.getHttpHeaders());
            return;
        } else {
            sendErrorResponse(rmo.requestMessage, rv);
            return;
        }
    }

    
/*    private void sendResponseMessage(BytesXMLMessage msg, RequestMessageObject rmo) throws JCSMPException {
        producer.sendReply(rmo.requestMessage, UsefulUtils.formatResponseMessage(msg, rmo));
    }
*/    
    private void consumeNext(RequestMessageObject rmo) {
        // param check
//        if (!rmo.check(Set.of("flowId","format"))) {
        if (!rmo.checkForAllowedParams("format")) {
            sendErrorResponse(rmo.requestMessage, ErrorTypes.INVALID_URL_PARAMS);
            return;
        }
        // check the queue has an active flow
        if (!flowManager.hasActiveFlow(rmo.resourceName)) {
            sendErrorResponse(rmo.requestMessage, ErrorTypes.INVALID_FLOW_ID);
            return;
        }
        // check that the passed flowId matches
//        if (!flowManager.getFlowId(rmo.resourceName).equals(rmo.getParam("flowId"))) {
//            sendErrorResponse(rmo.requestMessage, ErrorTypes.INVALID_FLOW_ID);
//            return;
//        }
        
        try {
            BytesXMLMessage msg = flowManager.getFlowFromId(rmo.resourceName).getNextMessage(rmo.requestCorrelationId);
            if (msg == null) {
                sendErrorResponse(rmo.requestMessage, 404, "no messages");
                return;
            } else {
                producer.sendReply(rmo.requestMessage, UsefulUtils.formatResponseMessage(msg, rmo));
            }
        } catch (JCSMPException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            sendErrorResponse(rmo.requestMessage, UsefulUtils.handleJcsmpException(e));
        }
    }
    
    private void ackMessage(RequestMessageObject rmo) {
        if (!rmo.checkForAllowedParams("msgId")) {
            sendErrorResponse(rmo.requestMessage, ErrorTypes.INVALID_URL_PARAMS);
            return;
        }
        Flow flow = flowManager.getFlowFromId(rmo.resourceName);
        // check this flowId exists
        if (flow == null) {
            sendErrorResponse(rmo.requestMessage, ErrorTypes.INVALID_FLOW_ID);
            return;
        }
        // verify that we've seen this message
        if (!flow.checkUnackedList(rmo.getParam("msgId"))) {
            sendErrorResponse(rmo.requestMessage, ErrorTypes.INVALID_MSG_ID);
            return;
        }
        // else, good to go!
        try {
            flow.getUnackedMessage(rmo.getParam("msgId")).ackMessage();
            System.out.println("Successfully ACKed "+rmo.getParam("msgId"));
            sendOkResponse(rmo.requestMessage,null);
        } catch (RuntimeException e) {
            logger.error("Caught while trying to ACK a message {} on flow {}",rmo.resourceName,rmo.getParam("msgId"));
            sendErrorResponse(rmo.requestMessage, UsefulUtils.handleJcsmpException(e));
        }
    }
    
    
    private void getSpecificMessage(RequestMessageObject rmo) {
      
        // param check
        if (!rmo.checkForAllowedParams("msgId","format")) {
            sendErrorResponse(rmo.requestMessage, ErrorTypes.INVALID_URL_PARAMS);
            return;
        }
        Flow flow = flowManager.getFlowFromId(rmo.resourceName);
        // check this flowId exists
        if (flow == null) {
            sendErrorResponse(rmo.requestMessage, ErrorTypes.INVALID_FLOW_ID);
            return;
        }
        // verify that we've seen this message
        if (!flow.checkUnackedList(rmo.getParam("msgId"))) {
            sendErrorResponse(rmo.requestMessage, ErrorTypes.INVALID_MSG_ID);
            return;
        }
        // looks good..!
        BytesXMLMessage msg = flow.getUnackedMessage(rmo.getParam("msgId"));
        try {
            producer.sendReply(rmo.requestMessage, UsefulUtils.formatResponseMessage(msg, rmo));
        } catch (JCSMPException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void getUnacked(RequestMessageObject rmo) {
        // param check
        if (!rmo.urlParams.isEmpty()) {
            sendErrorResponse(rmo.requestMessage, ErrorTypes.URL_PARAMS_NOT_EMPTY);
            return;
        }
        Flow flow = flowManager.getFlowFromId(rmo.resourceName);
        // check this flowId exists
        if (flow == null) {
            sendErrorResponse(rmo.requestMessage, ErrorTypes.INVALID_FLOW_ID);
            return;
        }
        JsonArrayBuilder jab = Json.createArrayBuilder();
        for (String msgId : flow.getUnackedMessageIds()) {
            jab.add(msgId);
        }
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add("msgId", jab);
        sendOkResponse(rmo.requestMessage, job.build().toString());
    }
    

    
    /*
Messaging Mode + Solace-Reply-Wait-Time-In-ms
^^^^^^^^^^^^^^^^^^ Start Message ^^^^^^^^^^^^^^^^^^^^^^^^^^^
Destination:                            Topic 'restQ/unbind/q3'
AppMessageID:                           ID:Solace-1913fbbb008f3c71
CorrelationId:                          ID:Solace-1913fbbb008f3c71
Class Of Service:                       USER_COS_1
DeliveryMode:                           DIRECT
Message Id:                             14
ReplyTo:                                Topic '#P2P/v:aaron-router/_rest-e84b5448e80a2ae9/restQ/unbind/q3'

^^^^^^^^^^^^^^^^^^ End Message ^^^^^^^^^^^^^^^^^^^^^^^^^^^

MicroGateway
^^^^^^^^^^^^^^^^^^ Start Message ^^^^^^^^^^^^^^^^^^^^^^^^^^^
Destination:                            Topic 'POST/restQ/unbind/q3'
AppMessageID:                           ID:Solace-daa9cca11075549c
CorrelationId:                          ID:Solace-daa9cca11075549c
Class Of Service:                       USER_COS_1
DeliveryMode:                           DIRECT
Message Id:                             15
ReplyTo:                                Topic '#P2P/v:aaron-router/_rest-2fa62ab9eecaf429/POST/restQ/unbind/q3'
TimeToLive:                             30000
User Property Map:                      4 entries
  Key 'JMS_Solace_HTTP_field_Accept' (String): * / *
  Key 'JMS_Solace_HTTP_field_User-Agent' (String): curl/7.68.0
  Key 'JMS_Solace_HTTP_method' (String): POST
  Key 'JMS_Solace_HTTP_target_path_query_verbatim' (String): restQ/unbind/q3
*/    
    
    
    @Override
    public void onReceive(BytesXMLMessage requestMessage) {
        System.out.printf(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>%nREQUEST MESSAGE:%n");
        System.out.println(requestMessage.dump());
//        System.out.println(">>> "+requestMessage.getDestination().getName());
        if (requestMessage.getDestination() instanceof Queue) {
            logger.error("GOT A QUEUE MESSAGE WHAAAAAAAT?");
            return;  // IGNORE! impossible!  lol
        } else if (requestMessage.getReplyTo() == null) {  // can't reply!?
            logger.error("GOT A MESSAGE WITH NO REPLY-TO");
            return;  // IGNORE!
        } else if (requestMessage.getCorrelationId() == null) {
            logger.error("GOT A MESSAGE WITHOUT A CORRELATION ID");
            return;  // IGNORE!
        }
        String topic = requestMessage.getDestination().getName();
        // e.g. topic == POST/restQ/bind/q1
        if (topic.split("/").length < 4) {
            sendErrorResponse(requestMessage, 400, "missing user properties or payload");
            return;
        }
        String resourceName = topic.split("/",4)[3];  // could be queue, or maybe flowId..?
        // we're assuming that this app is using MicroGateway mode, which auto-populates correlation ID & some User Properties
        String requestCorrelationId = UUID.randomUUID().toString();
//        String requestCorrelationId = requestMessage.getCorrelationId();
//        if (requestCorrelationId == null || !requestCorrelationId.matches(CORR_ID_REGEX)) {
//            sendErrorResponse(requestMessage, 400, "invalid/missing Correlation ID");
//            return;
//        }

        boolean restGatewayMode = requestMessage.getProperties() != null
                && requestMessage.getProperties().containsKey("JMS_Solace_HTTP_target_path_query_verbatim");
        boolean restMessagingMode = requestMessage instanceof TextMessage
                && requestMessage.getHTTPContentType().equals("application/json")
                && ((TextMessage)requestMessage).getText().length() > 0;
        System.out.println("gatway: "+restGatewayMode);
        System.out.println("messageing: "+restMessagingMode);
        // if either microgateway mode, or messaging mode with 
        if (!(restGatewayMode ^ restMessagingMode)) {  // XOR: it has to be one or the other, not both or neither
            sendErrorResponse(requestMessage, 400, "missing user properties or payload");
            return;
        }
        // now we're gonna parse all the stuff/params after the "?" in the URL
        Map<String, List<String>> urlParams = Collections.emptyMap();
        if (restGatewayMode) {
            try {
                // Key 'JMS_Solace_HTTP_target_path_query_verbatim' (String): restQ/rec/q1?msgId=ID:Solace-b19d76da378a830b&format=pretty
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
            } catch (SDTException e) {  // this really shouldn't happen!
                sendErrorResponse(requestMessage, UsefulUtils.handleJcsmpException(e));
                e.printStackTrace();
                logger.error(e);
                assert false;
                return;
            }
        }
        // now we start the topic demuxing process!!
        RequestMessageObject rmo = new RequestMessageObject(resourceName, requestMessage, requestCorrelationId, urlParams);
        
        /////////////////////////////////////
        // BIND TO A QUEUE
        if (topic.startsWith("POST/restQ/bind/") ||
                ( "accept get bind".equals("don't accept get bind") && topic.startsWith("GET/restQ/bind/"))) {
            bindToQueue(rmo);
        }
        /////////////////////////////////////
        // UNBIND FROM A QUEUE
        else if (topic.startsWith("POST/restQ/unbind/") ||
                ( "accept get bind".equals("don't accept get bind") && topic.startsWith("GET/restQ/bind/"))) {
            bindToQueue(rmo);
        }
        ////////////////////////////
        // CONSUME!  RECEIVE!
        else if (topic.startsWith("GET/restQ/rec/")) {
            consumeNext(rmo);
        }
        //////////////////////////
        // ACK consumed message
        else if (topic.startsWith("DELETE/restQ/ack/")) {  // ACKing a message!
            ackMessage(rmo);
        }
            
            
            

        else if (topic.startsWith("GET/queueBro/")) {  // browser!
            String flowId = "";
            if (flowId.isEmpty()) {
                sendErrorResponse(requestMessage, 400, "no unique \"id\" URL param included");
                return;
            }
//            if (!browsers.containsKey(queueName)) {
//                ReturnValue result = connectNewBrowse(parsedQueueName);
//                if (!result.isSuccess()) {
//                    // send error response
//                    sendErrorResponse(requestMessage, result);
//                    return;
//
//                }
//            }
                        
        }
        
        ////////////////////////////
        // GET MESSAGE!
        else if (topic.startsWith("GET/restQ/getMsg/")) {
            getSpecificMessage(rmo);
        }

        ////////////////////////////
        // LIST UNACKED MESSAGES!
        else if (topic.startsWith("GET/restQ/unacked/")) {
            getUnacked(rmo);
        }
        
        else {
            System.err.println("RECEIVED A MESSAGE THAT WE DON'T SUBSCRIBE TO!!!");
            System.err.println(requestCorrelationId);
//          System.err.println(UsefulUtils.jsonPrettyMsgCopy(requestMessage).getText());
//            System.err.println(UsefulUtils.jsonMsgCopy(requestMessage).getText());
//            System.err.println(requestMessage.dump());
            sendErrorResponse(requestMessage, 500, "unsupported!!!");
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
