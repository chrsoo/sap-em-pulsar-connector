# sap-em-pulsar-connector
SAP Enterprise Messaging Connector (`sap-em`) for Apache Pulsar. Provides 
both a **source** for consuming messagings from a SAP EM **queue** and **sink**
for producing messages to a SAP EM **queue** or publishing messaging to a SAP EM 
**topic**.

## Build
```
mvn clean package
```
## Test
Adapted from [Set up a standalone Pulsar locally](https://pulsar.apache.org/docs/en/standalone/) and 
[Managing Connectors](https://pulsar.apache.org/docs/en/io-managing/):


1. Open the **first** terminal window and change to the `sap-em-pulsar-connector` source directory
1. Download and unpack apache pulsar
    ```
    curl https://archive.apache.org/dist/pulsar/pulsar-2.6.1/apache-pulsar-2.6.1-bin.tar.gz -O
    tar xvfz apache-pulsar-2.6.1-bin.tar.gz
    ```
1. Add the `target` folder to the connector configuration
    ```
    sed -i -e 's|^connectorsDirectory: .*$|connectorsDirectory: ../target|g' \
    apache-pulsar-2.6.1/conf/functions_worker.yml
    ```
1. Start apache pulsar; logs are printed to standard out  
    ```
    apache-pulsar-2.6.1/bin/pulsar standalone
    ```     
1. Open a **second** terminal window and change to the `sap-em-pulsar-connector` source directory
1. Copy the `sap-em-source-example.yaml` and `sap-em-sink-example.yaml` examples to the pulsar home directory 
    ```
    cp sap-em-example.yaml apache-pulsar-2.6.1/sap-em-source.yaml
    cp sap-em-example.yaml apache-pulsar-2.6.1/sap-em-sink.yaml
    ```
1. Edit the `sap-em-source.yaml` and `sap-em-source.yaml` configs with the correct correct values for your 
SAP Enterprise Messaging instance.
1. Create and start the **sap-em-source** connector
    ```    
    apache-pulsar-2.6.1/bin/pulsar-admin sources create \
        --tenant public \
        --namespace default \
        --name  sap-em-source \
        --destination-topic-name sap-em-topic \
        --source-type sap-em \
        --source-config-file ./sap-em-source.yaml
    ```
    _Alternatively_ it can be run locally in its own window:
    ```
    apache-pulsar-2.6.1/bin/pulsar-admin sources localrun \
        --tenant public \
        --namespace default \
        --name  sap-em-source \
        --destination-topic-name sap-em-topic \
        --source-config-file ./sap-em-source.yaml \
        --archive ../target/sap-em-pulsar-connector-1.0.0-SNAPSHOT.nar 
    ```
1. Create and start the **sap-em-sink** connector
    ```    
    apache-pulsar-2.6.1/bin/pulsar-admin sinks create \
        --tenant public \
        --namespace default \
        --name  sap-em-sink \
        --inputs sap-em-topic \
        --sink-type sap-em \
        --sink-config-file ./sap-em-sink.yaml
    ```
    _Alternatively_ it can be run locally in its own window:
    ```
    apache-pulsar-2.6.1/bin/pulsar-admin sinks localrun \
        --tenant public \
        --namespace default \
        --name  sap-em-sink \
        --inputs sap-em-topic \
        --sink-config-file ./sap-em-sink.yaml \
        --archive ../target/sap-em-pulsar-connector-1.0.0-SNAPSHOT.nar
    ```  
 1. Retrieve an SAP Enterprise Messaging access token 
    ```
    export CLIENT_ID='<clientid>'
    export CLIENT_SECRET='<clientsecret>'
    export CLIENT_CREDENTIALS="$(echo "${CLIENT_ID}${CLIENT_SECRET}" | base64)"
    
    export TOKEN_ENDPOINT='<tokenendpoint>'
    
    export ACCESS_TOKEN=$(curl --location -c cookies.txt \
        --request POST "${TOKEN_ENDPOINT}?grant_type=client_credentials&response_type=token" \
        --header "Authorization: Basic ${CLIENT_CREDENTIALS}" | jq -r '.access_token')
    ```
    ... where the `CLIENT_CREDENTIALS` envar is the Base64 encoded `clientid` and `clientsecret` separated 
    by a single colon character and `TOKEN_ENDPOINT` is the URL for retrieving access tokens.
    
    Note that the above assumes you have [installed jq](https://stedolan.github.io/jq/tutorial/)
 1. Publish a test message to the SAP Enterprise Messaging queue you configured for the `sap-em-source`:
    ```
    export QUEUE_API="https://enterprise-messaging-pubsub.cfapps.eu10.hana.ondemand.com/messagingrest/v1/queues"
    
    export SAP2PULSAR="sap2pulsar"
    
    curl --location --request POST "${QUEUE_API}/${SAP2PULSAR}/messages" \
        --header 'x-qos: 0' \
        --header 'Authorization: Bearer ${ACCESS_TOKEN}' \
        --header 'Content-Type: application/json' \
        --data-raw '{
            "hello": "world"
        }'    
     ```
 1. Consume the test message from the queue connected to the SAP Enterprise Messaging topic you configured for the `sap-em-sink`:
    ```
    export PULSAR2SAP="pulsar2sap"
  
    curl --location
        --request POST "${QUEUE_API}/${PULSAR2SAP}/messages/consumption" \
        --header 'x-qos: 0' \   
        --header 'Authorization: Bearer ${ACCESS_TOKEN}' \
        --header 'Content-Type: application/json'   
    ```

## Use
Create a YAML configuration file similar to

```
configs:
  connectionName:   connection name
  xsappname:        application name
  tokenEndpoint:    token endpoint URL
  clientID:         client ID
  clientSecret:     client secret
  serviceURL:       SAP EM Service URL
  destination:      queue:name or topic:name
```

SAP Enterprise Messaging sinks may produce messages on a queue or publish messages to a topic. If the `queue:`
or `topic:` prefix is left out the destination is assumed to be a queue.

SAP Enterprise Messaging sources must consume messages from a queue. Subscriptions 
are managed in SAP Enterprise Messaging that will route messages from the topic
to a queue. 

Field                 | Required | Default  | Description
--------------------- | -------- | -------- | ------------
connectionName        | `true`   | -        | The connection name used for connecting to SAPEnterpriseMessaging.
xsappname             | `true`   | -        | SAP HANA XS application name.
clientID              | `true`   | -        | OAuth2 client id.
clientSecret          | `true`   | -        | OAuth2 client secret.
tokenEndpoint         | `true`   | -        | OAuth2 token endpoint URL.
serviceURL            | `true`   | -        | SAP Enterprise Messaging Service URL.
destination           | `true`   | -        | The SAPEnterpriseMessaging destination name optionally prefixed with 'topic:'; if not prefixed 'queue:' is assumed.   
protocol              | `false`  | amqp10ws | SAP Enterprise Messaging protocol.
maxReconnectAttemptsn | `false`  | 20       | Maximum number of attempts at reconnecting before giving up; -1 for unlimited retries.
initialReconnectDelay | `false`  | 3000     | Delay in millis before reconnecting after the first failure.
reconnectDelay        | `false`  | 5000     | Delay in millis between reeconnect attempts after the first.


### Create a sap-em sink
```
pulsar-admin sinks create \
--tenant public \
--namespace default \
--name  sap-em-sink \
--inputs sap-em-topic \
--sink-config-file sap-em-sink.yaml \
--archive target/sap-em-pulsar-connector-1.0.0-SNAPSHOT.nar
```

### Create a sap-em source
```
pulsar-admin sources create \
--tenant public \
--namespace default \
--name  sap-em-source \
--destination-topic-name sap-em-topic \
--source-config-file ./sap-em-source.yaml \
--archive ../target/sap-em-pulsar-connector-1.0.0-SNAPSHOT.nar 
```
## Develop

* [IntelliJ configuration](https://projectlombok.org/setup/intellij)    

## Reference

### SAP Enterprise Messaging examples

* [Messaging Client Java - Samples for Enterprise Messaging](https://github.com/SAP-samples/enterprise-messaging-client-java-samples)

### AMQP 1.0 Web Sockets

* [Advanced Message Queuing Protocol (AMQP) WebSocket Binding (WSB) Version 1.0](https://docs.oasis-open.org/amqp-bindmap/amqp-wsb/v1.0/amqp-wsb-v1.0.html)