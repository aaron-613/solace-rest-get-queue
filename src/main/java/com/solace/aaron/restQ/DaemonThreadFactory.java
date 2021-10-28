package com.solace.aaron.restQ;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class DaemonThreadFactory implements ThreadFactory {

    private final String name;
    private AtomicInteger count = new AtomicInteger(1);
   
    public DaemonThreadFactory(String name) {
        this.name = name;
    }
    
    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r, name + "_" + count.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    }

}
