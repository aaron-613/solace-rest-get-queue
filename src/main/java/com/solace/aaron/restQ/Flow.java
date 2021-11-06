package com.solace.aaron.restQ;

import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.JCSMPException;
import java.util.Set;

public interface Flow {

    public String getQueueName();
    
    public String getFlowId();
    
    BytesXMLMessage getNextMessage(String newMsgId) throws JCSMPException;
    BytesXMLMessage getUnackedMessage(String msgId);


//    public void ackMessage(String msgId);
    
    /** used by "get specific message" to verify we are currently holding this */
    public boolean checkUnackedList(String msgId);
    
    public Set<String> getUnackedMessageIds();
    
}
