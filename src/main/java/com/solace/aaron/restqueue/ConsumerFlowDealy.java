package com.solace.aaron.restqueue;

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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConsumerFlowDealy implements Flow {

    private static final int FLOW_TIMEOUT_SEC = 300;  // if this doesn't get an ACK or nextMsg in this time, we'll close the flow
    public static final int CONSUMER_FLOW_TRANSPORT_WINDOW_SIZE = 1;

    private final String queueName;
    private String flowId;
    private final FlowReceiver flowReceiver;
    final Map<String,BytesXMLMessage> unackedMessages = new HashMap<>();  // corrID -> message
    private ScheduledFuture<?> future = null;

    private static ScheduledExecutorService pool = Executors.newScheduledThreadPool(1, new DaemonThreadFactory("ConsFlow"));
    private static final Logger logger = LogManager.getLogger();  // log4j2, but could also use SLF4J, JCL, etc.
    
    private ConsumerFlowDealy(String queueName, String flowId, FlowReceiver flowReceiver) {
        this.queueName = queueName;
        this.flowId = flowId;
        this.flowReceiver = flowReceiver;
    }

    public String getQueueName() {
        return queueName;
    }

    public String getFlowId() {
        return flowId;
    }
    
    
    public static ConsumerFlowDealy connectToQueue(JCSMPSession session, String queueName, String reqCorrId)
            throws OperationNotSupportedException, JCSMPErrorResponseException, JCSMPException {
        // configure the queue API object locally
        final Queue queue = JCSMPFactory.onlyInstance().createQueue(queueName);
        // Create a Flow be able to bind to and consume messages from the Queue.
        final ConsumerFlowProperties flow_prop = new ConsumerFlowProperties();
        flow_prop.setEndpoint(queue);
        flow_prop.setAckMode(JCSMPProperties.SUPPORTED_MESSAGE_ACK_CLIENT);  // best practice
        flow_prop.setTransportWindowSize(CONSUMER_FLOW_TRANSPORT_WINDOW_SIZE);  // why not?  REST consumers aren't fast!
        flow_prop.setActiveFlowIndication(true);
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
            return new ConsumerFlowDealy(queueName, reqCorrId, flowQueueReceiver);
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
    
    
    class QueueTimeoutTimer implements Runnable {

        @Override
        public void run() {
            System.out.println("TIMEOUT!");
            synchronized (this) {
                System.out.println(queueName);
                flowReceiver.close();
            }
            
        }
        
    }
    
    
    public BytesXMLMessage getNextMessage(String msgId) throws JCSMPException {
        // this next line should be impossible if using MicroGateway, each corrId is randomized
        if (unackedMessages.containsKey(msgId)) throw new IllegalArgumentException("correlation-id already exists");
        if (future != null) {
            future.cancel(true);
        }
        try {
            flowReceiver.start();
            BytesXMLMessage msg = flowReceiver.receive(500);
            flowReceiver.stop();
            if (msg != null) unackedMessages.put(msgId, msg);
    
            future = pool.schedule(new QueueTimeoutTimer(), FLOW_TIMEOUT_SEC, TimeUnit.SECONDS);
    
            logger.debug(unackedMessages.toString());
            return msg;
        } catch (ClosedFacilityException e) {  // this Flow is shut!
            e.printStackTrace();
            throw e;
        }
    }


}
