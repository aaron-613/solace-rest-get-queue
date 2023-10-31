package com.solace.aaron.restQ;

import com.solacesystems.jcsmp.JCSMPErrorResponseException;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.OperationNotSupportedException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

interface FlowManager {
    
    static final int FLOW_INACTIVITY_TIMEOUT_SEC = 120;  // if this doesn't get an ACK or nextMsg in this time, we'll close the flow
    static final int FLOW_TRANSPORT_WINDOW_SIZE = 1;
    static final int FLOW_RECEIVE_MESSAGE_TIMEOUT_MS = 500;

    static ScheduledExecutorService pool = Executors.newScheduledThreadPool(1, new DaemonThreadFactory("FlowActivityTimer"));

    
    // methods:
    
    // returns a flow
    Flow connectToQueue(JCSMPSession session, RequestMessageObject rmo)
            throws OperationNotSupportedException, JCSMPErrorResponseException, JCSMPException;

    void unbind(String queueName, String flowId);

    /** is there currently an active flow to this queue? */
    boolean doesQueueHaveBoundFlow(String queueName);
    
    boolean doesFlowExist(String flowId);
    
    String getFlowIdForQueue(String queueName);
//    public Flow getFlow(String queueName);
    public Flow getFlowFromId(String flowId);
    
//    BytesXMLMessage getUnackedMessage(String queueName, String msgId);

    
    void shutdown();
    
    
}
