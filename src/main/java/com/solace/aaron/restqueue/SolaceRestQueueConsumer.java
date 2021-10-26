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
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SolaceRestQueueConsumer implements XMLMessageListener {

    
    private JCSMPSession session;
    private XMLMessageProducer producer;
    private XMLMessageConsumer consumer;
    private volatile boolean isShutdown = false;             // are we done?

    private static final JCSMPFactory f = JCSMPFactory.onlyInstance();
    private static final Logger logger = LogManager.getLogger();  // log4j2, but could also use SLF4J, JCL, etc.

    
    private Map<String,ConsumerFlowDealy> flows = new HashMap<>();
    private Map<String,Map<String,Browser>> browsers = new HashMap<>();

    
    private ReturnValue connectNewQueue(String queueName) {
        ConsumerFlowDealy cfd;
        try {
            cfd = ConsumerFlowDealy.connectToQueue(session, queueName);
            flows.put(queueName, cfd);
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
    
    public Map<String, List<String>> splitQuery(String fullUrl) {
        if (!fullUrl.contains("?")) return Collections.emptyMap();
        String paramStr = fullUrl.split("\\?",2)[1];
        return Arrays.stream(paramStr.split("&"))
                .map(this::splitQueryParameter)
                .collect(Collectors.groupingBy(SimpleImmutableEntry::getKey, LinkedHashMap::new, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }
    
    private static final Charset UTF8 = Charset.forName("UTF-8");

    public SimpleImmutableEntry<String, String> splitQueryParameter(String it) {
        final int idx = it.indexOf("=");
        final String key = idx > 0 ? it.substring(0, idx) : it;
        final String value = idx > 0 && it.length() > idx + 1 ? it.substring(idx + 1) : null;
        return new SimpleImmutableEntry<>(
            URLDecoder.decode(key, UTF8),
            URLDecoder.decode(value, UTF8)
        );
    }


    
    
    
    

    
    void sendErrorResponse(BytesXMLMessage origMsg, ReturnValue returnValue) {
        sendErrorResponse(origMsg, returnValue.getHttpReturnCode(), returnValue.getHttpReturnReason());
    }
    
    void sendErrorResponse(BytesXMLMessage origMsg, int code, String reason) {
        TextMessage replyMsg = f.createMessage(TextMessage.class);
//        replyMsg.setText(reason + ", sorry! \u1F613");
        StringBuilder sb = new StringBuilder().append(code).append(": ").append(reason).append(", sorry :-(\n");
        replyMsg.setText(sb.toString());
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

    void sendOkResponse(BytesXMLMessage origMsg) {
        BytesMessage replyMsg = f.createMessage(BytesMessage.class);
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
        
        session.addSubscription(f.createTopic("HEAD/restQ/keepalive/>"));  // heartbeat to keep flow or browse alive
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
    
    @Override
    public void onReceive(BytesXMLMessage requestMessage) {
        //System.out.println(requestMessage.dump());
        if (requestMessage.getDestination() instanceof Queue) return;  // IGNORE! impossible!  lol
        String topic = requestMessage.getDestination().getName();
        String parsedQueueName = topic.split("/",4)[3];  // will always work due to subscription, at least 3 levels
        // we're assuming that this app is using MicroGateway mode, which auto-populates correlation ID & some User Properties
        String correlationId = requestMessage.getCorrelationId();
        if (correlationId == null) {
            sendErrorResponse(requestMessage, 400, "no message Correlation ID present, use microgateway");
            return;
        }
        if (requestMessage.getProperties() == null) {
            sendErrorResponse(requestMessage, 400, "missing user properties, no map, use microgateway");
            return;
        }
        Map<String, List<String>> params = Collections.emptyMap();
        String parsedId = "";
        String parsedFormat = "json";
        try {
            String fullUrl = requestMessage.getProperties().getString("JMS_Solace_HTTP_target_path_query_verbatim");
            if (fullUrl == null) {
                sendErrorResponse(requestMessage, 400, "missing user property \"JMS_Solace_HTTP_target_path_query_verbatim\", use microgateway");
                return;
            }
            try {
                params = splitQuery(fullUrl);
            } catch (RuntimeException e) {
                sendErrorResponse(requestMessage, 400, "invalid URL parameter syntax");
                return;
            }
            System.out.println(params);
            if (params.get("id") != null) parsedId = params.get("id").get(0);
            if (params.get("format") != null) parsedFormat = params.get("format").get(0);
        } catch (SDTException e) {  // this really shouldn't happen!
            sendErrorResponse(requestMessage, handleJcsmpException(e));
            return;
        }
        if (topic.startsWith("GET/restQ/con/")) {
            if (!flows.containsKey(parsedQueueName)) {
                ReturnValue result = connectNewQueue(parsedQueueName);
                if (!result.isSuccess()) {
                    // send error response
                    sendErrorResponse(requestMessage, result);
                    return;
                }
            }
            try {
                BytesXMLMessage msg = flows.get(parsedQueueName).getNextMessage(correlationId);
                if (msg == null) {
                    sendErrorResponse(requestMessage, 404, "no messages");
                    return;
                } else {
                    if (parsedFormat.equals("dump")) {
                        producer.sendReply(requestMessage, MessageUtils.sdkperfDumpMsgCopy(msg));
                    } else {
                        producer.sendReply(requestMessage, MessageUtils.jsonMsgCopy(msg));
                    }
                    System.out.println(flows.get(parsedQueueName).unackedMessages.keySet());
                    /////////////////////////////////////////////////
                    //msg.ackMessage();
                    /////////////////////////////////////////////////
                }
            } catch (JCSMPException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } else if (topic.startsWith("DELETE/restQ/ack/")) {  // ACKing a message!
            if (!flows.containsKey(parsedQueueName)) {
                sendErrorResponse(requestMessage, 404, "queue consumer not active");
                return;
            }
            String ackId = null;
            if (params.get("ackId") != null) ackId = params.get("ackId").get(0);
            if (ackId == null) {
                sendErrorResponse(requestMessage, 400, "missing URL param 'ackId' for ACK correlation");
                return;
            }
            if (flows.get(parsedQueueName).unackedMessages.get(ackId) == null) {
                sendErrorResponse(requestMessage, 404, "unknown ackId '"+ackId+"' for queue "+parsedQueueName);
                return;
            }
            // else, good to go!
            flows.get(parsedQueueName).unackedMessages.get(ackId).ackMessage();
            System.out.println("Successfully ACKed "+flows.get(parsedQueueName).unackedMessages.get(ackId));
            flows.get(parsedQueueName).unackedMessages.remove(ackId);
            sendOkResponse(requestMessage);
            
            
            
            

        } else if (topic.startsWith("GET/queueBro/")) {  // browser!
            if (parsedId.isEmpty()) {
                sendErrorResponse(requestMessage, 400, "no unique \"id\" URL param included");
                return;
            }
            if (!browsers.containsKey(parsedQueueName)) {
//                ReturnValue result = connectNewBrowse(parsedQueueName);
//                if (!result.isSuccess()) {
//                    // send error response
//                    sendErrorResponse(requestMessage, result);
//                    return;
//
//                }

            }
                        
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
