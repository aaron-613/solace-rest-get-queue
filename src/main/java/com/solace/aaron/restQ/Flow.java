package com.solace.aaron.restQ;

import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.JCSMPException;

public interface Flow {

    public BytesXMLMessage getNextMessage(String msgId) throws JCSMPException;
}
