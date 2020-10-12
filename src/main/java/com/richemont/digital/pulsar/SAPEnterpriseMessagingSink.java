package com.richemont.digital.pulsar;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.apache.pulsar.functions.api.Record;
import org.apache.pulsar.io.core.Sink;
import org.apache.pulsar.io.core.SinkContext;
import org.apache.pulsar.io.core.annotations.Connector;
import org.apache.pulsar.io.core.annotations.IOType;
import org.slf4j.Logger;

import javax.jms.*;
import java.util.Map;

/**
 * A simple connector to move messages from a Pulsar topic to an SAP Enterprise Messaging queue.
 */
@Connector(
        name = "sap-em",
        type = IOType.SINK,
        help = "A simple connector to move messages from a Pulsar topic to an SAP Enterprise Messaging queue.",
        configClass = SAPEnterpriseMessagingSinkConfig.class
)
public class SAPEnterpriseMessagingSink extends SAPEnterpriseMessagingConnector implements Sink<byte[]> {

    private Connection rabbitMQConnection;
    private Channel rabbitMQChannel;
    private SAPEnterpriseMessagingSinkConfig config;
    private String exchangeName;
    private String defaultRoutingKey;

    private Logger log;
    private MessageProducer producer;
    private Session session;

    // -- Sink

    @Override
    public void open(Map<String, Object> configMap, SinkContext context) throws Exception {
        config = SAPEnterpriseMessagingSinkConfig.load(configMap);
        config.validate();
        log = context.getLogger();
        reconnect(config);
    }

    @Override
    public void write(final Record<byte[]> record) {
        byte[] value = record.getValue();
        String key = record.getKey().orElseGet(() -> config.getRoutingKey());
        try {
            BytesMessage message = session.createBytesMessage();
            if(key != null) {
                message.setStringProperty(JMSX_GROUP_ID, key);
            }
            message.writeBytes(value);
            producer.send(message, new CompletionListener() {
                @Override
                public void onCompletion(Message message) {
                    record.ack();
                }
                @Override
                public void onException(Message message, Exception exception) {
                    record.fail();
                }
            });
        } catch (JMSException e) {
            record.fail();
            log.warn("failed to publish the message to SAP Enterprise Messaging: {}", e.getMessage());
        }
    }

    // -- SAPEnterpriseMessagingConnector

    @Override
    void doReconnect(Session session, Queue queue) throws JMSException {
        this.session = session;
        producer = session.createProducer(queue);
    }

}
