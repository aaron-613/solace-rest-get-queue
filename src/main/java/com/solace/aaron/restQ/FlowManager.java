package com.solace.aaron.restQ;

import com.solacesystems.jcsmp.JCSMPErrorResponseException;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.OperationNotSupportedException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

interface FlowManager {
    
    static final int FLOW_TIMEOUT_SEC = 300;  // if this doesn't get an ACK or nextMsg in this time, we'll close the flow
    static final int FLOW_TRANSPORT_WINDOW_SIZE = 1;

    static ScheduledExecutorService pool = Executors.newScheduledThreadPool(1, new DaemonThreadFactory("FlowActivityTimer"));

    
    // methods:
    
    // returns a flowId
    String connectToQueue(JCSMPSession session, RequestMessageObject rmo)
            throws OperationNotSupportedException, JCSMPErrorResponseException, JCSMPException;

    void unbind(String queueName, String flowId);

    /** is there currently an active flow to this queue? */
    boolean hasActiveQueueFlow(String queueName);
    
    String getFlowId(String queueName);
//    public Flow getFlow(String queueName);
    public Flow getFlowFromId(String flowId);
    
//    BytesXMLMessage getUnackedMessage(String queueName, String msgId);

    
    void shutdown();
    
    
}
