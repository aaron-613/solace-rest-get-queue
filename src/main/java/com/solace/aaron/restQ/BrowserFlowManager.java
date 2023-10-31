package com.solace.aaron.restQ;

import com.solacesystems.jcsmp.Browser;
import com.solacesystems.jcsmp.BrowserProperties;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.ClosedFacilityException;
import com.solacesystems.jcsmp.JCSMPErrorResponseException;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.OperationNotSupportedException;
import com.solacesystems.jcsmp.Queue;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class BrowserFlowManager implements FlowManager {


    private Map<String,String> queueToFlowIdMap = new HashMap<>();
    private Map<String,String> flowIdToQueueMap = new HashMap<>();
    private Map<String,BrowserFlow> queueToFlowMap = new HashMap<>();
    private Map<String,BrowserFlow> flowIdToFlowMap = new HashMap<>();

    private static final Logger logger = LogManager.getLogger();  // log4j2, but could also use SLF4J, JCL, etc.
    
    
    // empty constructor!
    
    
    public Flow connectToQueue(JCSMPSession session, RequestMessageObject rmo)
            throws OperationNotSupportedException, JCSMPErrorResponseException, JCSMPException {
        String queueName = rmo.resourceName;
        // configure the queue API object locally
        final Queue queue = JCSMPFactory.onlyInstance().createQueue(queueName);
        
        BrowserProperties br_prop = new BrowserProperties();
        br_prop.setEndpoint(queue);
        br_prop.setTransportWindowSize(FlowManager.FLOW_TRANSPORT_WINDOW_SIZE);
        br_prop.setWaitTimeout(FlowManager.FLOW_RECEIVE_MESSAGE_TIMEOUT_MS);
        String selector = rmo.getParam("selector");  // might be null if not set
        if (selector != null) br_prop.setSelector(selector);
        System.out.printf("Attempting to browse to queue '%s' on the broker.%n", queueName);
        Browser myBrowser;
        try {
            myBrowser = session.createBrowser(br_prop);
            System.out.println("SUCCESS!");
            BrowserFlow flow = new BrowserFlow(queueName, rmo.uuid, myBrowser);
            flow.restartTimer();
            queueToFlowIdMap.put(queueName,flow.getFlowId());
            flowIdToQueueMap.put(flow.getFlowId(), queueName);
            queueToFlowMap.put(queueName, flow);
            flowIdToFlowMap.put(flow.getFlowId(), flow);
//            return new ConsumerFlow(queueName, reqCorrId, flowQueueReceiver);
            return flow;
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
        
        BrowserFlow flow = flowIdToFlowMap.get(flowId);
        flow.browser.close();
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
    
    @Override
    public Flow getFlowFromId(String flowId) {
        return flowIdToFlowMap.get(flowId);
    }

    /**
     * For every FlowReceiver, initiate close()
     */
    @Override
    public void shutdown() {
        for (BrowserFlow flow : flowIdToFlowMap.values()) {
            flow.browser.close();
        }
    }
    
    /////////////////////////////////////////////////
    // INNER CLASS

    private class BrowserFlow implements Flow {
        
        private final String queueName;             // obvious
        private final String flowId;                                   // the auto-gen flowId, derived from original MicroGateway request correlationid
        private final String magicKey = UUID.randomUUID().toString();  // needed to close the flow
        private final Browser browser;                 // the JCSMP flow receiver to receive messages on
        private final Map<String,BytesXMLMessage> unackedMessages = new HashMap<>();  // corrID -> message
        private ScheduledFuture<?> futureTask = null;       // timer for inactivity
        private boolean readOnlyBrowser = true;

        private BrowserFlow(String queueName, String flowId, Browser browser) {
            this.queueName = queueName;
            this.flowId = flowId;
            this.browser = browser;
        }

        @Override
        public String getQueueName() {
            return queueName;
        }

        @Override
        public String getFlowId() {
            return flowId;
        }
        
        public String getMagicKey() {
            return magicKey;
        }
        
        
        private void restartTimer() {
            if (futureTask != null) {
                futureTask.cancel(true);
            }
            futureTask = pool.schedule(new FlowInactivityTimeoutTimer(this), FLOW_INACTIVITY_TIMEOUT_SEC, TimeUnit.SECONDS);
        }

        @Override
        public void close() {
            browser.close();
        }
        

        @Override
        public BytesXMLMessage getNextMessage(String newMsgId) throws JCSMPException {
            // this next line should be impossible if using MicroGateway, each corrId is randomized
            if (unackedMessages.containsKey(newMsgId)) throw new AssertionError("correlation-id already exists!?");
            restartTimer();
            try {
                BytesXMLMessage msg = browser.getNext();
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
