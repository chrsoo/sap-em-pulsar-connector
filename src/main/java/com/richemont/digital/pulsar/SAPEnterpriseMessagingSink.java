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
import java.util.Set;

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

            Set<Map.Entry<String, String>> entries = record.getProperties().entrySet();
            for(Map.Entry<String, String> entry : entries) {
                message.setStringProperty(entry.getKey(), entry.getValue());
            }

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
        } catch(JMSException e) {
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
