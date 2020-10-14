package com.richemont.digital.pulsar;

/*-
 * #%L
 * pulsar-sap-em-connector
 * %%
 * Copyright (C) 2020 Richemont SA
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import org.apache.commons.lang3.StringUtils;
import org.apache.pulsar.functions.api.Record;
import org.apache.pulsar.io.core.Source;
import org.apache.pulsar.io.core.SourceContext;
import org.apache.pulsar.io.core.annotations.Connector;
import org.apache.pulsar.io.core.annotations.IOType;
import org.slf4j.Logger;

import javax.jms.*;
import java.lang.IllegalStateException;
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
    configClass = SAPEnterpriseMessagingConfig.class)
public class SAPEnterpriseMessagingSource extends SAPEnterpriseMessagingConnector implements Source<byte[]> {

    private SAPEnterpriseMessagingConfig config;
    private Session session;

    private MessageConsumer consumer;
    private Logger log;

    // -- Source


    public void open(Map<String, Object> configMap, SourceContext context) throws Exception {
        config = SAPEnterpriseMessagingConfig.load(configMap);
        config.validate();
        log = context.getLogger();
        reconnect(config);
    }

    @Override
    public Record<byte[]> read() throws Exception {
        Message message = receiveMessage();
        Record<byte[]> record = createRecord(session, message);

        String key, value;
        String id = message.getJMSMessageID();
        Enumeration keys = message.getPropertyNames();
        Map<String, String> properties = record.getProperties();
        while(keys.hasMoreElements()) {
            key = (String) keys.nextElement();
            value = message.getStringProperty(key);
            properties.put(key, value);
            log.trace("{} - property {}: '{}'", id, key, value);
        }

        return record;
    }

    // -- SAPEnterpriseMessagingConnector

    @Override
    protected void doReconnect(Session session, Queue queue) throws JMSException {
        this.session = session;
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
        }

        return message;
    }

    private SAPEnterpriseMessagingRecord createRecord(Session session, Message message) throws Exception {
        String key = message.getStringProperty(JMSX_GROUP_ID);
        if(message instanceof BytesMessage) {
            BytesMessage bytesMessage = (BytesMessage) message;
            byte[] byteData = new byte[(int) bytesMessage.getBodyLength()];
            bytesMessage.readBytes(byteData);
            return new SAPEnterpriseMessagingRecord(message, key, byteData, session);
        } else if(message instanceof TextMessage) {
            TextMessage textMessage = (TextMessage) message;
            byte[] byteData = textMessage.getText().getBytes();
            return new SAPEnterpriseMessagingRecord(message, key, byteData, session);
        } else {
            String id = message.getJMSMessageID();
            log.warn("{} - unsupported JMS message {}", id, message.getClass());
            close();
            throw new RuntimeException("unhandled JMS message " + message.getClass());
        }
    }

    static private class SAPEnterpriseMessagingRecord implements Record<byte[]> {

        private final Message message;
        private final String key;
        private final byte[] value;
        private final Session session;

        SAPEnterpriseMessagingRecord(Message message, String key, byte[] value, Session session) {
            this.message = message;
            this.key = key;
            this.value = value;
            this.session = session;
        }


        @Override
        public void ack() {
            try {
                message.acknowledge();
            } catch (JMSException e) {
                throw new IllegalStateException("message acknowledge failed", e);
            }
        }

        @Override
        public void fail() {
            try {
                session.rollback();
            } catch (JMSException e) {
                throw new IllegalStateException("message rollback failed", e);
            }
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
