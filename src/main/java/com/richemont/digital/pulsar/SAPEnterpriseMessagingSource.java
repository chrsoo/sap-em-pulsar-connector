package com.richemont.digital.pulsar;

import org.apache.commons.lang3.StringUtils;
import org.apache.pulsar.functions.api.Record;
import org.apache.pulsar.io.core.Source;
import org.apache.pulsar.io.core.SourceContext;
import org.apache.pulsar.io.core.annotations.Connector;
import org.apache.pulsar.io.core.annotations.IOType;
import org.slf4j.Logger;

import javax.jms.*;
import java.util.Enumeration;
import java.util.Map;
import java.util.Optional;

/**
 * A simple connector to move messages from a SAP Enterprise Messaging queue to a Pulsar topic.
 */
@Connector(
    name = "sap-em",
    type = IOType.SOURCE,
    help = "A simple connector to move messages from a SAP Enterprise Messaging queue to a Pulsar topic",
    configClass = SAPEnterpriseMessagingSourceConfig.class)
public class SAPEnterpriseMessagingSource extends SAPEnterpriseMessagingConnector implements Source<byte[]> {

    private SAPEnterpriseMessagingSinkConfig config;
    private SourceContext context;

    private MessageConsumer consumer;
    private Logger log;

    // -- Source


    public void open(Map<String, Object> configMap, SourceContext context) throws Exception {
        this.context = context;

        config = SAPEnterpriseMessagingSinkConfig.load(configMap);
        config.validate();
        log = context.getLogger();
        reconnect(config);
    }

    @Override
    public Record<byte[]> read() throws Exception {
        Message message = receiveMessage();

        Record<byte[]> record;
        String key = getRoutingKey(message, config.getRoutingKey());
        if(message instanceof BytesMessage) {
            BytesMessage bytesMessage = (BytesMessage) message;
            byte[] byteData = new byte[(int) bytesMessage.getBodyLength()];
            bytesMessage.readBytes(byteData);
            record = new SAPEnterpriseMessagingRecord(key, byteData);
        } else if(message instanceof TextMessage) {
            TextMessage textMessage = (TextMessage) message;
            byte[] byteData = textMessage.getText().getBytes();
            record = new SAPEnterpriseMessagingRecord(key, byteData);
        } else {
            String id = message.getJMSMessageID();
            log.warn("{} - unsupported JMS message {}", id, message.getClass());
            close();
            throw new RuntimeException("unhandled JMS message " + message.getClass());
        }

        message.acknowledge();
        return record;
    }

    // -- SAPEnterpriseMessagingConnector

    @Override
    protected void doReconnect(Session session, Queue queue) throws JMSException {
        consumer = session.createConsumer(queue);
        log.debug("created consumer for {} session", config);
    }

    // -- SAPEnterpriseMessagingSource

    private Message receiveMessage() throws JMSException {
        // FIXME find out which exception is thrown for the five minute inactivity and reconnect
        Message message = consumer.receive();
        if(log.isTraceEnabled()) {
            String id = message.getJMSMessageID();
            log.trace("{} - JMSType: {}", id, message.getJMSType());
            log.trace("{} - messageClass: {}", id, message.getClass());
            log.trace("{} - correlationID: {}", id, message.getJMSCorrelationID());
            Enumeration names = message.getPropertyNames();
            String name;
            while(names.hasMoreElements()) {
                name = (String) names.nextElement();
                log.trace("{} - property {}: '{}'", id, name, message.getObjectProperty(name));
            }
        }

        return message;
    }

    /**
     * Return the <a href="https://activemq.apache.org/message-groups">JMSXGroupID</a> and default provided key is empty.
     * @param message JMS message
     * @param key default to the provided key
     * @return the routing key for the message
     * @throws JMSException if the JMSXGroupID could not be read
     */
    private String getRoutingKey(Message message, String key) throws JMSException {
        String jmsxGroupID = message.getStringProperty(JMSX_GROUP_ID);
        return StringUtils.isEmpty(jmsxGroupID) ? key : jmsxGroupID;
    }

    static private class SAPEnterpriseMessagingRecord implements Record<byte[]> {

        private final String key;
        private final byte[] value;

        private SAPEnterpriseMessagingRecord(String key, byte[] value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public Optional<String> getKey() {
            return key == null ? Optional.empty() : Optional.of(key);
        }

        @Override
        public byte[] getValue() {
            return value;
        }
    }
}
