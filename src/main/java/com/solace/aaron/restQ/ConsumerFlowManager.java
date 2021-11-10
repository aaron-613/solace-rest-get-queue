package com.solace.aaron.restQ;

import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.ClosedFacilityException;
import com.solacesystems.jcsmp.ConsumerFlowProperties;
import com.solacesystems.jcsmp.FlowEventArgs;
import com.solacesystems.jcsmp.FlowEventHandler;
import com.solacesystems.jcsmp.FlowReceiver;
import com.solacesystems.jcsmp.JCSMPErrorResponseException;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.OperationNotSupportedException;
import com.solacesystems.jcsmp.Queue;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class ConsumerFlowManager implements FlowManager {


    private Map<String,String> queueToFlowIdMap = new HashMap<>();
    private Map<String,String> flowIdToQueueMap = new HashMap<>();
    private Map<String,ConsumerFlow> queueToFlowMap = new HashMap<>();
    private Map<String,ConsumerFlow> flowIdToFlowMap = new HashMap<>();

    private static final Logger logger = LogManager.getLogger();  // log4j2, but could also use SLF4J, JCL, etc.
    
    
    // empty constructor!
    
    
    public String connectToQueue(JCSMPSession session, RequestMessageObject rmo)
            throws OperationNotSupportedException, JCSMPErrorResponseException, JCSMPException {
        String queueName = rmo.resourceName;
        // configure the queue API object locally
        final Queue queue = JCSMPFactory.onlyInstance().createQueue(queueName);
        // Create a Flow be able to bind to and consume messages from the Queue.
        final ConsumerFlowProperties flow_prop = new ConsumerFlowProperties();
        flow_prop.setEndpoint(queue);
        flow_prop.setAckMode(JCSMPProperties.SUPPORTED_MESSAGE_ACK_CLIENT);  // ACK later manually
        flow_prop.setTransportWindowSize(FlowManager.FLOW_TRANSPORT_WINDOW_SIZE);  // why not?  REST consumers aren't fast!
        flow_prop.setActiveFlowIndication(true);
        String selector = rmo.getParam("selector");  // might be null if not set

//        if (!rmo.payloadString.isEmpty()) {
//            JsonReader reader = Json.createReader(new StringReader(rmo.payloadString));
//            try {
//                JsonObject json = reader.readObject();
//                selector = json.getString("selector");
//            } catch (RuntimeException e) {
//                throw e;
//            }
//        }
        if (selector != null) flow_prop.setSelector(selector);
        System.out.printf("Attempting to bind to queue '%s' on the broker.%n", queueName);
        FlowReceiver flowQueueReceiver = null;
        try {
            flowQueueReceiver = session.createFlow(null, flow_prop, null, new FlowEventHandler() {
                @Override
                public void handleEvent(Object source, FlowEventArgs event) {
                    // ### Type: 'FLOW_RECONNECTED', Info: 'OK', ResponseCode: '200', Exception: 'null'
                    // ### Type: 'FLOW_ACTIVE', Info: 'Flow becomes active', ResponseCode: '0', Exception: 'null'
                    System.out.printf("### Flow event for '%s': %s%n",((FlowReceiver)source).getEndpoint(),event);
                }
            });  // super basic blocking/sync queue receiver
            System.out.println("SUCCESS!");
            ConsumerFlow flow = new ConsumerFlow(queueName, rmo.uuid, flowQueueReceiver);
            flow.restartTimer();
            queueToFlowIdMap.put(queueName,flow.getFlowId());
            flowIdToQueueMap.put(flow.getFlowId(), queueName);
            queueToFlowMap.put(queueName, flow);
            flowIdToFlowMap.put(flow.getFlowId(), flow);
//            return new ConsumerFlow(queueName, reqCorrId, flowQueueReceiver);
            return flow.getFlowId();
        } catch (OperationNotSupportedException e) {  // not allowed to do this
            logger.error("Nope, couldn't do that!",e);
            throw e;
        } catch (JCSMPErrorResponseException e) {  // something else went wrong: queue not exist, queue shutdown, etc.
            logger.error("Nope, couldn't do that!",e);
            throw e;
        } catch (JCSMPException e) {
            logger.error("Nope, couldn't do that!",e);
            throw e;
        }
    }

    @Override
    public void unbind(String queueName, String flowId) {
        assert queueName.equals(flowIdToQueueMap.get(flowId));
        assert flowId.equals(queueToFlowIdMap.get(queueName));
        assert queueToFlowMap.get(queueName).equals(flowIdToFlowMap.get(flowId));
        
        ConsumerFlow flow = flowIdToFlowMap.get(flowId);
        flow.flowReceiver.close();
        queueToFlowIdMap.remove(queueName);  // get rid of queue-mapped objects
        queueToFlowMap.remove(queueName);
        // but leave the flowId-mapped objects just in case
    }

    

    @Override
    public boolean doesQueueHaveBoundFlow(String queueName) {
        System.out.println(">> queueToFlowIdMap.containsKey?  "+queueName+"  --> "+queueToFlowIdMap.containsKey(queueName));
        System.out.println(">> queueToFlowMap.containsKey?  "+queueName+"  --> "+queueToFlowMap.containsKey(queueName));
        return queueToFlowIdMap.containsKey(queueName);
    }

    @Override
    public boolean doesFlowExist(String flowId) {
        System.out.println(">> flowIdToFlowMap.containsKey?  "+flowId+"  --> "+flowIdToFlowMap.containsKey(flowId));
        System.out.println(">> flowIdToQueueMap.containsKey?  "+flowId+"  --> "+flowIdToQueueMap.containsKey(flowId));
        return flowIdToFlowMap.containsKey(flowId);
    }

    
    @Override
    public String getFlowIdForQueue(String queueName) {
        return queueToFlowIdMap.get(queueName);
    }
    
    
//    @Override
//    public Flow getFlow(String queueName) {
//        return queueToFlowMap.get(queueName);
//    }

    @Override
    public Flow getFlowFromId(String flowId) {
        return flowIdToFlowMap.get(flowId);
    }

//    @Override
//    public BytesXMLMessage getUnackedMessage(String queueName, String msgId) {
//        // checks should already have been done to confirm this queue is legit
//        assert queueToFlowMap.containsKey(queueName);
//        return queueToFlowMap.get(queueName).unackedMessages.get(msgId);
//    }

    /**
     * For every FlowReceiver, initiate close()
     */
    @Override
    public void shutdown() {
        for (ConsumerFlow flow : flowIdToFlowMap.values()) {
            flow.flowReceiver.close();
        }
    }
    
    /////////////////////////////////////////////////
    // INNER CLASS

    private class ConsumerFlow implements Flow {
        
        private final String queueName;             // obvious
        private String flowId;                      // the auto-gen flowId, derived from original MicroGateway request correlationid
        private final FlowReceiver flowReceiver;    // the JCSMP flow receiver to receive messages on
        final Map<String,BytesXMLMessage> unackedMessages = new HashMap<>();  // corrID -> message
        private ScheduledFuture<?> futureTask = null;       // timer for inactivity

        private ConsumerFlow(String queueName, String flowId, FlowReceiver flowReceiver) {
            this.queueName = queueName;
            this.flowId = flowId;
            this.flowReceiver = flowReceiver;
        }

        @Override
        public String getQueueName() {
            return queueName;
        }

        @Override
        public String getFlowId() {
            return flowId;
        }
        
//        BytesXMLMessage getUnackedMessage(String msgId) {
//            return unackedMessages.get(msgId);
//        }
        

        class QueueTimeoutTimer implements Runnable {
            @Override
            public void run() {
                System.out.println("TIMEOUT!");
                synchronized (this) {  // what are we synchronizing on???
                    System.out.println(queueName);
                    flowReceiver.close();  // during a timeout, just close the FlowReceiver
                    // but leave all the maps alone
                }
            }
        }
        
        private void restartTimer() {
            if (futureTask != null) {
                futureTask.cancel(true);
            }
            futureTask = pool.schedule(new QueueTimeoutTimer(), FLOW_TIMEOUT_SEC, TimeUnit.SECONDS);
        }

        
        

        @Override
        public BytesXMLMessage getNextMessage(String newMsgId) throws JCSMPException {
            // this next line should be impossible if using MicroGateway, each corrId is randomized
            if (unackedMessages.containsKey(newMsgId)) throw new AssertionError("correlation-id already exists!?");
            restartTimer();
            try {
                flowReceiver.start();
                BytesXMLMessage msg = flowReceiver.receive(500);
                flowReceiver.stop();
                if (msg != null) unackedMessages.put(newMsgId, msg);  // track this message for ACKing later
                logger.debug(unackedMessages.toString());
                return msg;
            } catch (ClosedFacilityException e) {  // this Flow is shut!
                e.printStackTrace();
                throw e;
            }
        }
        
        @Override
        public BytesXMLMessage getUnackedMessage(String msgId) {
            assert unackedMessages.containsKey(msgId);
            restartTimer();
            return unackedMessages.get(msgId);
        }

        
//        @Override
//        public void ackMessage(String msgId) throws IllegalStateException {
//            assert unackedMessages.containsKey(msgId);
//            unackedMessages.get(msgId).ackMessage();
//        }

        @Override
        public boolean checkUnackedList(String msgId) {
            restartTimer();
            return unackedMessages.containsKey(msgId);
        }

        @Override
        public Set<String> getUnackedMessageIds() {
            restartTimer();
            return unackedMessages.keySet();
        }
    }
    // END INNER ///////////////////////////////////////////////



}
