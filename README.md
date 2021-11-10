# solace-rest-get-queue

This stateful microservice is used to provide a REST-based interface to receive/acknowledge persistent messages from Solace queues.

The application uses the JCSMP API to connect to the same Solace Message VPN where the queues are, and subscribes using Direct messaging topics to listen for incoming REST requests.

The application is designed to work with a Message VPN in either (Micro)Gateway mode or ~~REST Messaging mode~~ (not yet!).  It does not provide a true "RESTful" API as it violates some conventions (e.g. calling GET on Flow returns next message on the Flow, which is not idempotent); this API is more RPC-style... most of these calls should probably be `POST`.


## REST API for MicroGateway

```
bind()      POST     /restQ/bind/<queueName>                --> replies with flowId & magicKey
receive()   GET      /restQ/recv/<flowId>?format=<format>   --> replies with msgId and formatted message
ackMsg()    DELETE   /restQ/ack/$flowId?msgId=$msgId
unbind()    DELETE   /restQ/unbind/$flowId?magicKey=$magicKey

            GET      /restQ/unacked/<flowId>     --> retrieves list of all unacked msgIds on this flow
            GET      /restQ/getMsg/<msgId>       --> retrieves previous unacked message
            HEAD     /restQ/keepalive/<flowId>   --> ensures Flow does not close due to inactivity
```
Examples: 
[`bind()`](#bind-to-queue)
[`receive()`](#receive-one-message-from-queue-using-flowid)

### REST Messaging

If the Message VPN is configured with Messaging Mode, the initiating REST requestor must do a few different things:
- Prepend the REST verb + `/` to the front of the URL
- REST Messaging mode doesn't support URL query parameters, any required params must be passed as a JSON payload
- Supply a reply header: `solace-reply-wait-time-in-ms`
- Ensure Content-Type is set correctly

E.g.:
```
curl -u clientUsername:password http://localhost:9000/GET/restQ/recv/cb4faa8e-e330-44a0-94d5-fc3ff4d78d42 -X POST -d '{"format":"pretty"}' -H 'solace-reply-wait-time-in-ms: 5000' -H 'content-type: application/json'
```

# Getting Started - Examples

## Download and Build

```
$ git clone https://github.com/aaron-613/solace-rest-get-queue
   // (or download zip file and unzip)

$ cd solace-rest-get-queue

$ ./gradlew assemble
Starting a Gradle Daemon (subsequent builds will be faster)

BUILD SUCCESSFUL in 34s
6 actionable tasks: 6 executed

$ cd build/distributions

$ unzip solace-rest-get-queue.zip

$ cd solace-rest-get-queue

$ bin/solace-rest-get-queue localhost default asdf
SolaceRestQueueConsumer initializing...
SolaceRestQueueConsumer connected, and running. Press [ENTER] to quit.
```

## Bind to queue:
Assumes that Solace Message VPN has REST interface exposed on port 9000.
```
curl -u clientUsername:password http://localhost:9000/restQ/bind/q1 -X POST
```
```json
{"flowId":"5447c6cd-985f-4369-a27b-ce6f503bdafc"}

```

## Receive one message from queue using flowId:

### Default compact JSON representation
```
curl -u clientUsername:password http://localhost:9000/restQ/recv/5447c6cd-985f-4369-a27b-ce6f503bdafc
```
```json
{"msgId":"175801ff-9d99-4960-954b-0c0db279f461","message":{"destination":"bridge/testing/A000001MWC/5581561693/Good","destinationType":"Topic","applicationMessageId":"d7de924928d1f5dd1478e95c7ab2c72e2b1cedd9","cos":"USER_COS_1","deliveryMode":"NON_PERSISTENT","mesageId":"42772","priority":4,"redelivered":true,"replicationGroupMessageId":"rmid1:0a4f3-1f694db795f-00000000-0000a714","sequenceNumber":1,"messageClass":"TextMessage","payload":{"dataTag":"value","UAI_DFA":"A000001MWC","UAI_SRC":"A00000L6G1","dfaTimestamp":"2020-12-22T10:44:17.810Z","nodeId":"ns=13;b=ef0fab18f5755a4a17e9d3297a16c1c7","hashcode":"d7de924928d1f5dd1478e95c7ab2c72e2b1cedd9","msgId":"5581561693","values":{"value":{"dataType":"Float","arrayType":"Scalar","value":-0.09},"statusCode":{"value":0,"description":"No Error","name":"Good"},"sourceTimestamp":"2020-12-22T10:44:16.945Z","sourcePicoseconds":971000000,"serverTimestamp":"2020-12-22T10:44:16.989Z","serverPicoseconds":973500000}}}}
```


### Pretty-Print JSON representation (format=pretty)
```
curl -u clientUsername:password http://localhost:9000/restQ/recv/5447c6cd-985f-4369-a27b-ce6f503bdafc?format=pretty
```
```json
{
    "msgId": "ff851856-67a0-41fa-9889-0c23b8bf29b4",
    "message": {
        "destination": "bridge/testing/A000001MWC/5581561693/Good",
        "destinationType": "Topic",
        "applicationMessageId": "d7de924928d1f5dd1478e95c7ab2c72e2b1cedd9",
        "cos": "USER_COS_1",
        "deliveryMode": "NON_PERSISTENT",
        "messageId": "42772",
        "priority": 4,
        "redelivered": true,
        "replicationGroupMessageId": "rmid1:0a4f3-1f694db795f-00000000-0000a714",
        "sequenceNumber": 1,
        "messageClass": "TextMessage",
        "payload": {
            "dataTag": "value",
            "UAI_DFA": "A000001MWC",
            "UAI_SRC": "A00000L6G1",
            "dfaTimestamp": "2020-12-22T10:44:17.810Z",
            "nodeId": "ns=13;b=ef0fab18f5755a4a17e9d3297a16c1c7",
            "hashcode": "d7de924928d1f5dd1478e95c7ab2c72e2b1cedd9",
            "msgId": "5581561693",
            "values": {
                "value": {
                    "dataType": "Float",
                    "arrayType": "Scalar",
                    "value": -0.09
                },
                "statusCode": {
                    "value": 0,
                    "description": "No Error",
                    "name": "Good"
                },
                "sourceTimestamp": "2020-12-22T10:44:16.945Z",
                "sourcePicoseconds": 971000000,
                "serverTimestamp": "2020-12-22T10:44:16.989Z",
                "serverPicoseconds": 973500000
            }
        }
    }
}
```

### SdkPerf "dump()"-style Representation (format=dump)

```
curl -u clientUsername:password http://localhost:9000/restQ/recv/5447c6cd-985f-4369-a27b-ce6f503bdafc?format=dump
```
```
RestQ msgId:                            8e75adce-d786-4344-af3b-a89b77dbbf7a

Destination:                            Topic 'bridge/testing/A000001MWC/5581561693/Good'
AppMessageID:                           d7de924928d1f5dd1478e95c7ab2c72e2b1cedd9
SequenceNumber:                         1
Priority:                               4
Class Of Service:                       USER_COS_1
DeliveryMode:                           NON_PERSISTENT
Message Id:                             42772
Message Re-delivered
Replication Group Message ID:           rmid1:0a4f3-1f694db795f-00000000-0000a714
Binary Attachment:                      len=527
  1d 02 0f 7b 22 64 61 74    61 54 61 67 22 3a 22 76    ...{"dataTag":"v
  61 6c 75 65 22 2c 22 55    41 49 5f 44 46 41 22 3a    alue","UAI_DFA":
  22 41 30 30 30 30 30 31    4d 57 43 22 2c 22 55 41    "A000001MWC","UA
  49 5f 53 52 43 22 3a 22    41 30 30 30 30 30 4c 36    I_SRC":"A00000L6
  47 31 22 2c 22 64 66 61    54 69 6d 65 73 74 61 6d    G1","dfaTimestam
  70 22 3a 22 32 30 32 30    2d 31 32 2d 32 32 54 31    p":"2020-12-22T1
  30 3a 34 34 3a 31 37 2e    38 31 30 5a 22 2c 22 6e    0:44:17.810Z","n
  6f 64 65 49 64 22 3a 22    6e 73 3d 31 33 3b 62 3d    odeId":"ns=13;b=
  65 66 30 66 61 62 31 38    66 35 37 35 35 61 34 61    ef0fab18f5755a4a
  31 37 65 39 64 33 32 39    37 61 31 36 63 31 63 37    17e9d3297a16c1c7
  22 2c 22 68 61 73 68 63    6f 64 65 22 3a 22 64 37    ","hashcode":"d7
  64 65 39 32 34 39 32 38    64 31 66 35 64 64 31 34    de924928d1f5dd14
  37 38 65 39 35 63 37 61    62 32 63 37 32 65 32 62    78e95c7ab2c72e2b
  31 63 65 64 64 39 22 2c    22 6d 73 67 49 64 22 3a    1cedd9","msgId":
  22 35 35 38 31 35 36 31    36 39 33 22 2c 22 76 61    "5581561693","va
  6c 75 65 73 22 3a 7b 22    76 61 6c 75 65 22 3a 7b    lues":{"value":{
  22 64 61 74 61 54 79 70    65 22 3a 22 46 6c 6f 61    "dataType":"Floa
  74 22 2c 22 61 72 72 61    79 54 79 70 65 22 3a 22    t","arrayType":"
  53 63 61 6c 61 72 22 2c    22 76 61 6c 75 65 22 3a    Scalar","value":
  2d 30 2e 30 39 7d 2c 22    73 74 61 74 75 73 43 6f    -0.09},"statusCo
  64 65 22 3a 7b 22 76 61    6c 75 65 22 3a 30 2c 22    de":{"value":0,"
  64 65 73 63 72 69 70 74    69 6f 6e 22 3a 22 4e 6f    description":"No
  20 45 72 72 6f 72 22 2c    22 6e 61 6d 65 22 3a 22    .Error","name":"
  47 6f 6f 64 22 7d 2c 22    73 6f 75 72 63 65 54 69    Good"},"sourceTi
  6d 65 73 74 61 6d 70 22    3a 22 32 30 32 30 2d 31    mestamp":"2020-1
  32 2d 32 32 54 31 30 3a    34 34 3a 31 36 2e 39 34    2-22T10:44:16.94
  35 5a 22 2c 22 73 6f 75    72 63 65 50 69 63 6f 73    5Z","sourcePicos
  65 63 6f 6e 64 73 22 3a    39 37 31 30 30 30 30 30    econds":97100000
  30 2c 22 73 65 72 76 65    72 54 69 6d 65 73 74 61    0,"serverTimesta
  6d 70 22 3a 22 32 30 32    30 2d 31 32 2d 32 32 54    mp":"2020-12-22T
  31 30 3a 34 34 3a 31 36    2e 39 38 39 5a 22 2c 22    10:44:16.989Z","
  73 65 72 76 65 72 50 69    63 6f 73 65 63 6f 6e 64    serverPicosecond
  73 22 3a 39 37 33 35 30    30 30 30 30 7d 7d 00       s":973500000}}.
```


