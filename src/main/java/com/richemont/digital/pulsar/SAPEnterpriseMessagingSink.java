/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.richemont.digital.pulsar;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.pulsar.functions.api.Record;
import org.apache.pulsar.io.core.Sink;
import org.apache.pulsar.io.core.SinkContext;
import org.apache.pulsar.io.core.annotations.Connector;
import org.apache.pulsar.io.core.annotations.IOType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * A Simple RabbitMQ sink, which transfer records from Pulsar to RabbitMQ.
 * This class expects records from Pulsar to have values that are stored as bytes or string.
 *
 * <b>This class was blatantly stolen from https://github.com/apache/pulsar/blob/master/pulsar-io/rabbitmq/src/main/java/org/apache/pulsar/io/rabbitmq/RabbitMQSource.java</b>
 *
 * TODO Refactor for handling AMQP10WS protocol of SAP Enterprise Messaging
 *
 */
@Connector(
        name = "rabbitmq",
        type = IOType.SINK,
        help = "A sink connector is used for moving messages from Pulsar to RabbitMQ.",
        configClass = SAPEnterpriseMessagingSinkConfig.class
)
public class SAPEnterpriseMessagingSink implements Sink<byte[]> {

    private static final Logger log = LoggerFactory.getLogger(SAPEnterpriseMessagingSink.class);
    private Connection rabbitMQConnection;
    private Channel rabbitMQChannel;
    private SAPEnterpriseMessagingSinkConfig SAPEnterpriseMessagingSinkConfig;
    private String exchangeName;
    private String defaultRoutingKey;

    @Override
    public void open(Map<String, Object> config, SinkContext sinkContext) throws Exception {
        SAPEnterpriseMessagingSinkConfig = SAPEnterpriseMessagingSinkConfig.load(config);
        SAPEnterpriseMessagingSinkConfig.validate();

        ConnectionFactory connectionFactory = SAPEnterpriseMessagingSinkConfig.createConnectionFactory();
        rabbitMQConnection = connectionFactory.newConnection(SAPEnterpriseMessagingSinkConfig.getConnectionName());
        log.info("A new connection to {}:{} has been opened successfully.",
                rabbitMQConnection.getAddress().getCanonicalHostName(),
                rabbitMQConnection.getPort()
        );

        exchangeName = SAPEnterpriseMessagingSinkConfig.getExchangeName();
        defaultRoutingKey = SAPEnterpriseMessagingSinkConfig.getRoutingKey();
        String exchangeType = SAPEnterpriseMessagingSinkConfig.getExchangeType();

        rabbitMQChannel = rabbitMQConnection.createChannel();
        String queueName = SAPEnterpriseMessagingSinkConfig.getQueueName();
        if (StringUtils.isNotEmpty(queueName)) {
            rabbitMQChannel.exchangeDeclare(exchangeName, BuiltinExchangeType.DIRECT, true);
            rabbitMQChannel.queueDeclare(SAPEnterpriseMessagingSinkConfig.getQueueName(), true, false, false, null);
            rabbitMQChannel.queueBind(SAPEnterpriseMessagingSinkConfig.getQueueName(), exchangeName, defaultRoutingKey);
        } else {
            rabbitMQChannel.exchangeDeclare(exchangeName, exchangeType, true);
        }
    }

    @Override
    public void write(Record<byte[]> record) {
        byte[] value = record.getValue();
        try {
            String routingKey = record.getProperties().get("routingKey");
            rabbitMQChannel.basicPublish(exchangeName, StringUtils.isEmpty(routingKey) ? defaultRoutingKey : routingKey, null, value);
            record.ack();
        } catch (IOException e) {
            record.fail();
            log.warn("Failed to publish the message to RabbitMQ ", e);
        }
    }

    @Override
    public void close() throws Exception {
        if (rabbitMQChannel != null) {
            rabbitMQChannel.close();
        }
        if (rabbitMQConnection != null) {
            rabbitMQConnection.close();
        }
    }
}
