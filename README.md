# solace-rest-get-queue

This stateful microserve is used to provide a REST-based interface to consume/acknowledge Solace persistent messages from queues.

The application connects to the same Solace Message VPN where the queues are, and using Direct messaging subscribes to topics to listen to incoming REST requests.

The application is designed to work with a Message VPN in either (Micro)Gateway mode or REST Messaging mode.

## URL way (µgw & msg'ing)


```
POST   restQ/bind/<queueName>    --> replies with flowId
POST   restQ/unbind/<queueName>

POST   restQ/con/<flowId>        --> replies with msg & msgId
GET    restQ/con/<flowId>     (optional, depending on program options)

POST   restQ/ack/<msgId>
DELETE restQ/ack/<msgId>     (optional)

GET    restQ/unacked/<flowId>    --> retrieves list of all unacked msgIds on this flow
GET    restQ/getMsg/<msgId>      --> retrieves previous unacked message

```

### REST Messaging

If the Message VPN is configured with Messaging Mode, the initiating REST requestor must prepend the REST verb + `/` to the front of the URL.  E.g.:
```
curl -u clientName:password http://localhost:9000/POST/restQ/bind/q1 -X POST
```

