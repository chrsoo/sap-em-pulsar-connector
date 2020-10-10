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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import org.apache.pulsar.functions.api.Record;
import org.apache.pulsar.io.core.PushSource;
import org.apache.pulsar.io.core.SourceContext;
import org.apache.pulsar.io.core.annotations.Connector;
import org.apache.pulsar.io.core.annotations.IOType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple connector to consume messages from a RabbitMQ queue
 *
 * <b>This class was blatantly stolen from https://github.com/apache/pulsar/blob/master/pulsar-io/rabbitmq/src/main/java/org/apache/pulsar/io/rabbitmq/RabbitMQSource.java</b>
 *
 * TODO Refactor for handling AMQP10WS protocol of SAP Enterprise Messaging
 */
@Connector(
    name = "rabbitmq",
    type = IOType.SOURCE,
    help = "A simple connector to move messages from a RabbitMQ queue to a Pulsar topic",
    configClass = SAPEnterpriseMessagingSourceConfig.class)
public class SAPEnterpriseMessagingSource extends PushSource<byte[]> {

    private static Logger logger = LoggerFactory.getLogger(SAPEnterpriseMessagingSource.class);

    private Connection rabbitMQConnection;
    private Channel rabbitMQChannel;
    private SAPEnterpriseMessagingSourceConfig SAPEnterpriseMessagingSourceConfig;

    @Override
    public void open(Map<String, Object> config, SourceContext sourceContext) throws Exception {
        SAPEnterpriseMessagingSourceConfig = SAPEnterpriseMessagingSourceConfig.load(config);
        SAPEnterpriseMessagingSourceConfig.validate();

        ConnectionFactory connectionFactory = SAPEnterpriseMessagingSourceConfig.createConnectionFactory();
        rabbitMQConnection = connectionFactory.newConnection(SAPEnterpriseMessagingSourceConfig.getConnectionName());
        logger.info("A new connection to {}:{} has been opened successfully.",
                rabbitMQConnection.getAddress().getCanonicalHostName(),
                rabbitMQConnection.getPort()
        );
        rabbitMQChannel = rabbitMQConnection.createChannel();
        rabbitMQChannel.queueDeclare(SAPEnterpriseMessagingSourceConfig.getQueueName(), false, false, false, null);
        logger.info("Setting channel.basicQos({}, {}).",
                SAPEnterpriseMessagingSourceConfig.getPrefetchCount(),
                SAPEnterpriseMessagingSourceConfig.isPrefetchGlobal()
        );
        rabbitMQChannel.basicQos(SAPEnterpriseMessagingSourceConfig.getPrefetchCount(), SAPEnterpriseMessagingSourceConfig.isPrefetchGlobal());
        com.rabbitmq.client.Consumer consumer = new RabbitMQConsumer(this, rabbitMQChannel);
        rabbitMQChannel.basicConsume(SAPEnterpriseMessagingSourceConfig.getQueueName(), consumer);
        logger.info("A consumer for queue {} has been successfully started.", SAPEnterpriseMessagingSourceConfig.getQueueName());
    }

    @Override
    public void close() throws Exception {
        rabbitMQChannel.close();
        rabbitMQConnection.close();
    }

    private class RabbitMQConsumer extends DefaultConsumer {
        private SAPEnterpriseMessagingSource source;

        public RabbitMQConsumer(SAPEnterpriseMessagingSource source, Channel channel) {
            super(channel);
            this.source = source;
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            source.consume(new RabbitMQRecord(Optional.ofNullable(envelope.getRoutingKey()), body));
            long deliveryTag = envelope.getDeliveryTag();
            // positively acknowledge all deliveries up to this delivery tag to reduce network traffic
            // since manual message acknowledgments are turned on by default
            this.getChannel().basicAck(deliveryTag, true);
        }
    }

    static private class RabbitMQRecord implements Record<byte[]> {

        private final Optional<String> key;
        private final byte[] value;

        private RabbitMQRecord(Optional<String> key, byte[] value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public Optional<String> getKey() {
            return key;
        }

        @Override
        public byte[] getValue() {
            return value;
        }

    }
}
