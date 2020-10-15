# sap-em-pulsar-connector
SAP Enterprise Messaging Connector for Apache Pulsar

## Build
```
mvn clean package
```
## Test
Adapted from [Set up a standalone Pulsar locally](https://pulsar.apache.org/docs/en/standalone/) and 
[Managing Connectors](https://pulsar.apache.org/docs/en/io-managing/):

Open a terminal, change to the `sap-em-pulsar-connector` source directory and do the following...

1. Download and unpack apache pulsar
    ```
    curl https://archive.apache.org/dist/pulsar/pulsar-2.6.1/apache-pulsar-2.6.1-bin.tar.gz -O
    tar xvfz apache-pulsar-2.6.1-bin.tar.gz
    ```
1. Edit the connector configuration
    ```
    sed -i -e 's|^connectorsDirectory: .*$|connectorsDirectory: ../target|g' apache-pulsar-2.6.1/conf/functions_worker.yml
    ```
1. Start apache pulsar; logs are printed to standard out  
    ```
    apache-pulsar-2.6.1/bin/pulsar standalone
    ```     
Open another terminal in the `sap-em-pulsar-connector` source directory and do...
1. Copy the sap-em source example to the pulsar home directory 
    ```
    cp sap-em-source-example.yaml sap-em-source.yaml
    ```
1. Edit sap-em-source.yaml with correct config for SAP Enterise Messaging
1. Create and start the sap-em-source connector and run it locally 
    ```
    apache-pulsar-2.6.1/bin/pulsar-admin sources localrun \
    --tenant public \
    --namespace default \
    --name  sap-em-source \
    --destination-topic-name sap-em-topic \
    --source-config-file ./sap-em-source.yaml \
	-a ../target/sap-em-pulsar-connector-1.0.0-SNAPSHOT.nar 
    ```
    Run on server (WIP)...    
    ```    
    apache-pulsar-2.6.1/bin/pulsar-admin sources create \
        --tenant public \
        --namespace default \
        --name  sap-em-source \
        --destination-topic-name sap-em-topic \
        --source-type sap-em \
        --source-config-file ./sap-em-source.yaml
    ```
1. Start consuming messages from SAP Enterprise Messaging in Apache Pulsar
    ```
    apache-pulsar-2.6.1/bin/pulsar-client consume sap-em-topic -s "test-subscription"
    ```
1. Publish messages to your SAP Enterprise Messaging Queue that you configured for the source!

## Deploy

TODO

## Use

Field                 | Required | Default  | Description
--------------------- | -------- | -------- | ------------
connectionName        | `true`   | -        | The connection name used for connecting to SAPEnterpriseMessaging.
xsappname             | `true`   | -        | SAP HANA XS application name.
clientID              | `true`   | -        | OAuth2 client id.
clientSecret          | `true`   | -        | OAuth2 client secret.
tokenEndpoint         | `true`   | -        | OAuth2 token endpoint URL.
serviceURL            | `true`   | -        | SAP Enterprise Messaging Service URL.
queueName             | `true`   | -        | The SAPEnterpriseMessaging queue name from which messages should be read from or written to.
protocol              | `false`  | amqp10ws | SAP Enterprise Messaging protocol.
maxReconnectAttemptsn | `false`  | 20       | Maximum number of attempts at reconnecting before giving up; -1 for unlimited retries.
initialReconnectDelay | `false`  | 3000     | Delay in millis before reconnecting after the first failure.
reconnectDelay        | `false`  | 5000     | Delay in millis between reeconnect attempts after the first.

## Develop

### IntelliJ configuration

* https://projectlombok.org/setup/intellij    

## Reference

### SAP Enterprise Messaging examples

* [Messaging Client Java - Samples for Enterprise Messaging](https://github.com/SAP-samples/enterprise-messaging-client-java-samples)

### AMQP 1.0 Web Sockets

* [Advanced Message Queuing Protocol (AMQP) WebSocket Binding (WSB) Version 1.0](https://docs.oasis-open.org/amqp-bindmap/amqp-wsb/v1.0/amqp-wsb-v1.0.html)