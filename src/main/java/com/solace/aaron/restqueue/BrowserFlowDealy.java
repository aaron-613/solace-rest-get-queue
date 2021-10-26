package com.solace.aaron.restqueue;

import com.solacesystems.jcsmp.Browser;
import com.solacesystems.jcsmp.BrowserProperties;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.ClosedFacilityException;
import com.solacesystems.jcsmp.JCSMPErrorResponseException;
import com.solacesystems.jcsmp.JCSMPErrorResponseSubcodeEx;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
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

public class BrowserFlowDealy {

    private final String queueName;
    private final Browser browser;
    final Map<String,BytesXMLMessage> seenMessages = new HashMap<>();  // corrID -> message
    private ScheduledFuture<?> future = null;

    private static ScheduledExecutorService pool = Executors.newScheduledThreadPool(1, new DaemonThreadFactory("Browser"));
    
    private static final Logger logger = LogManager.getLogger();  // log4j2, but could also use SLF4J, JCL, etc.

    
    public BrowserFlowDealy(String queueName, Browser browser) {
        this.queueName = queueName;
        this.browser = browser;
    }

    public String getQueueName() {
        return queueName;
    }

    public Browser getBrowser() {
        return browser;
    }
    
    
    public static BrowserFlowDealy connectNewBrowse(JCSMPSession session, String queueName, String sessionId)
            throws OperationNotSupportedException, JCSMPErrorResponseException, JCSMPException{
        // configure the queue API object locally
        final Queue queue = JCSMPFactory.onlyInstance().createQueue(queueName);
        // Create a Flow be able to bind to and consume messages from the Queue.
        
        BrowserProperties br_prop = new BrowserProperties();
        br_prop.setEndpoint(queue);
        br_prop.setTransportWindowSize(1);
        br_prop.setWaitTimeout(1000);
        try {
            Browser myBrowser = session.createBrowser(br_prop);
//            if (!browsers.containsKey(queueName)) {
//                browsers.put(queueName, new HashMap<>());
//            }
//            browsers.get(queueName).put(sessionId, myBrowser);
            return new BrowserFlowDealy(queueName, myBrowser);
        } catch (OperationNotSupportedException e) {  // not allowed to do this
            logger.error("Nope, couldn't do that!",e);
            throw e;
        } catch (JCSMPErrorResponseException e) {  // something else went wrong: queue not exist, queue shutdown, etc.
            logger.error("Nope, couldn't do that!",e);
            JCSMPErrorResponseException e2 = (JCSMPErrorResponseException)e;
            logger.error(JCSMPErrorResponseSubcodeEx.getSubcodeAsString(e2.getSubcodeEx()) + ": " + e.getResponsePhrase());
            throw e;
        } catch (JCSMPException e) {
            logger.error("Nope, couldn't do that!",e);
            throw e;
        }
    }

    
    class BrowserTimeoutTimer implements Runnable {

        @Override
        public void run() {
            System.out.println("BROWSER TIMEOUT!");
            synchronized (this) {
                System.out.println(queueName);
                browser.close();
            }
            
        }
        
    }

    public BytesXMLMessage getNextMessage(String correlationId) throws JCSMPException {
        // this next line should be impossible if using MicroGateway, each corrId is randomized
        if (seenMessages.containsKey(correlationId)) throw new IllegalArgumentException("correlation-id already exists");
        if (future != null) {
            future.cancel(true);
        }
        try {
            if (!browser.hasMore()) return null;
            BytesXMLMessage msg = browser.getNext(100);
            if (msg != null) seenMessages.put(correlationId, msg);
            future = pool.schedule(new BrowserTimeoutTimer(), 60, TimeUnit.SECONDS);
//            logger.debug(unackedMessages.toString());
            return msg;
        } catch (ClosedFacilityException e) {  // this Flow is shut!
            e.printStackTrace();
            return null;
        }
    }

    
    public boolean deleteMsg(String corId) throws JCSMPException {
        BytesXMLMessage msg = seenMessages.get(corId);
        browser.remove(msg);
        return true;
    }

    
}
