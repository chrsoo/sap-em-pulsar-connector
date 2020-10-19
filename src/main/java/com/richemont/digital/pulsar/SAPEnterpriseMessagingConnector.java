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

import com.sap.cloud.servicesdk.xbem.extension.sapcp.jms.MessagingServiceJmsConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.io.IOException;
import java.util.Map;

public abstract class SAPEnterpriseMessagingConnector implements AutoCloseable {

    static final String JMSX_GROUP_ID = "JMSXGroupID";
    protected Logger log = LoggerFactory.getLogger(SAPEnterpriseMessagingConnector.class);
    private Connection connection;
    private SAPEnterpriseMessagingConfig config;

    // -- AutoCloseable

    @Override
    final public void close() throws Exception {
        if(connection == null) {
            log.debug("connection already closed or was never opened");
            return;
        }
        log.debug("closing connection");
        connection.close();
        log.info("closed connection");
        connection = null;
    }

    // -- SAPEnterpriseMessagingConnector

    private Connection createConnection(SAPEnterpriseMessagingConfig config) throws JMSException {
        MessagingServiceJmsConnectionFactory factory = config.getMessagingServiceJmsConnectionFactory();
        return factory.createConnection();
//        return config.getUsername() == null
//                ? factory.createConnection()
//                : factory.createConnection(config.getUsername(), config.getPassword());
    }

    final void open(Map<String, Object> configMap, Logger logger) throws JMSException, IOException {
        config = SAPEnterpriseMessagingConfig.load(configMap);
        config.validate();
        log = logger;

        connection = createConnection(config);
        log.debug("created connection for {} config", config);

        Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        log.debug("created session for {} connection", config);

        Destination destination = createJMSDestination(session);
        connect(session, destination);

        connection.start();
        log.info("listening for messages on {}", config);
    }

    private Destination createJMSDestination(Session session) throws JMSException {
        String jmsDestination = config.getJMSDestination();

        return jmsDestination.startsWith("queue:")
                ? session.createQueue(jmsDestination)
                : session.createTopic(jmsDestination);
    }


    abstract void connect(Session session, Destination queue) throws JMSException;

    // -- getters

    protected SAPEnterpriseMessagingConfig getConfig() {
        return config;
    }
}
