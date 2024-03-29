package com.solace.aaron.restQ;

import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.JCSMPException;
import java.util.Set;

public interface Flow {

    public String getQueueName();
    
    public String getFlowId();
    public String getMagicKey();
    public void close();
    
    BytesXMLMessage getNextMessage(String newMsgId) throws JCSMPException;
    BytesXMLMessage getUnackedMessage(String msgId);


//    public void ackMessage(String msgId);
    
    /** used by "get specific message" to verify we are currently holding this */
    public boolean checkUnackedList(String msgId);
    
    public Set<String> getUnackedMessageIds();
    
    class FlowInactivityTimeoutTimer implements Runnable {
        
        final Flow flow;
        
        FlowInactivityTimeoutTimer(Flow flow) {
            this.flow = flow;
        }
        
        @Override
        public void run() {
            System.out.println("TIMEOUT ON "+flow);
            synchronized (flow) {  // what are we synchronizing on???
                flow.close();  // during a timeout, just close the FlowReceiver
                // but leave all the maps alone
            }
        }
    }

}
