package com.richemont.digital.pulsar;

import com.sap.cloud.servicesdk.xbem.extension.sapcp.jms.MessagingServiceJmsConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.Session;

public abstract class SAPEnterpriseMessagingConnector implements AutoCloseable {

    static final String JMSX_GROUP_ID = "JMSXGroupID";
    private static final Logger log = LoggerFactory.getLogger(SAPEnterpriseMessagingConnector.class);
    private Connection connection;

    // -- AutoCloseable

    @Override
    final public void close() throws Exception {
        if(connection == null) {
            log.debug("connection already closed or was never opened");
        } else {
            log.debug("closing connection");
            connection.close();
            log.info("closed connection");
        }
        connection = null;
    }

    // -- SAPEnterpriseMessagingConnector

    private Connection createConnection(SAPEnterpriseMessagingContext config) throws JMSException {
        MessagingServiceJmsConnectionFactory factory = config.getMessagingServiceJmsConnectionFactory();
        return config.getUsername() == null
                ? factory.createConnection()
                : factory.createConnection(config.getUsername(), config.getPassword());
    }

    final void reconnect(SAPEnterpriseMessagingContext config) throws JMSException {
        connection = createConnection(config);
        log.debug("created connection for {} config", config);

        Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        log.debug("created session for {} connection", config);

        Queue queue = session.createQueue(config.getDestination());
        doReconnect(session, queue);

        connection.start();
        log.info("listening for messages on {}", config);
    }

    abstract void doReconnect(Session session, Queue queue) throws JMSException;
}
