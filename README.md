# solace-rest-get-queue

This stateful microserve is used to provide a REST-based interface for Solace persistent consumers to consume/acknowledge messages off Solace queues.

The application connects to the same Solace Message VPN where the queues are, and using Direct messaging subscribes to topics to listen to incoming REST requests.

The application is designed to work with a Message VPN in either (Micro)Gateway mode or REST Messaging mode.


```
POST   restQ/bind/<queueName>    --> replies with flowId
POST   restQ/unbind/<queueName>
POST   restQ/con/<flowId>        --> replies with msg & msgId
GET    restQ/con/<flowId>
POST   restQ/ack/<messageId>
DELETE restQ/ack/<messageId>
```

### REST Messaging

If the Message VPN is configured with Messaging Mode, the initiating REST requestor must prepend the REST verb + `/` to the front of the URL.  E.g.:
```
curl -u clientName:password http://localhost:9000/POST/restQ/bind/q1
```

