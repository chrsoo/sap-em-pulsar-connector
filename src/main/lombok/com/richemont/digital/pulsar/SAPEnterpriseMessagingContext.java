package com.richemont.digital.pulsar;

import com.google.common.base.Preconditions;
import com.rabbitmq.client.ConnectionFactory;
import com.sap.cloud.servicesdk.xbem.core.MessagingService;
import com.sap.cloud.servicesdk.xbem.core.MessagingServiceFactory;
import com.sap.cloud.servicesdk.xbem.core.exception.MessagingException;
import com.sap.cloud.servicesdk.xbem.core.impl.MessagingServiceFactoryCreator;
import com.sap.cloud.servicesdk.xbem.extension.sapcp.jms.MessagingServiceJmsConnectionFactory;
import com.sap.cloud.servicesdk.xbem.extension.sapcp.jms.MessagingServiceJmsSettings;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.pulsar.io.core.annotations.FieldDoc;
import org.springframework.cloud.Cloud;
import org.springframework.cloud.CloudFactory;
import org.springframework.cloud.service.ServiceConnectorConfig;

import javax.jms.Queue;
import java.io.Serializable;

/**
 * Configuration object for all SAPEnterpriseMessaging components.
 */
@Data
@Accessors(chain = true)
public abstract class SAPEnterpriseMessagingContext implements Serializable {

    private static final long serialVersionUID = 1L;

    @FieldDoc(
            required = true,
            defaultValue = "",
            help = "The connection name used for connecting to SAPEnterpriseMessaging")
    private String connectionName;

    @FieldDoc(
            required = true,
            defaultValue = "",
            help = "The SAPEnterpriseMessaging host to connect to")
    private String host;

    @FieldDoc(
            required = true,
            defaultValue = "443",
            help = "The SAPEnterpriseMessaging port to connect to")
    private int port = 443;

    @FieldDoc(
            required = false,
            defaultValue = "guest",
            sensitive = true,
            help = "The username used to authenticate to SAPEnterpriseMessaging")
    private String username = "guest";

    @FieldDoc(
            required = false,
            defaultValue = "guest",
            sensitive = true,
            help = "The password used to authenticate to SAPEnterpriseMessaging")
    private String password = "guest";

    @FieldDoc(
            required = false,
            defaultValue = "",
            help = "The SAPEnterpriseMessaging queue name from which messages should be read from or written to")
    private String queueName;

    @FieldDoc(
            required = false,
            defaultValue = "20",
            help = "Maximum number of attempts at reconnecting before giving up; -1 for unlimited retries")
    private int maxReconnectAttempts= 20;

    @FieldDoc(
            required = false,
            defaultValue = "3000",
            help = "Delay in millis before reconnecting after the first failure.")
    private int initialReconnectDelay= 3000;

    @FieldDoc(
            required = false,
            defaultValue = "3000",
            help = "Delay in millis between reeconnect attempts after the first")
    private int reconnectDelay= 5000;

    public void validate() {
        Preconditions.checkNotNull(host, "host property not set.");
        Preconditions.checkNotNull(port, "port property not set.");
        Preconditions.checkNotNull(queueName, "queueName property not set.");
        Preconditions.checkNotNull(connectionName, "connectionName property not set.");
    }

    String getDestination() {
        return queueName.startsWith("queue:") ? queueName : "queue:" + queueName;
    }

    MessagingServiceJmsConnectionFactory getMessagingServiceJmsConnectionFactory() {
        Cloud cloud = new CloudFactory().getCloud();
        MessagingService service = cloud.getSingletonServiceConnector(
                MessagingService.class, null);

        if (service == null) {
            throw new IllegalStateException("Unable to create the MessagingService.");
        }

        MessagingServiceFactory factory = MessagingServiceFactoryCreator.createFactory(service);

        try {
            MessagingServiceJmsSettings settings = new MessagingServiceJmsSettings();
            settings.setMaxReconnectAttempts(maxReconnectAttempts); // use -1 for unlimited attempts
            settings.setInitialReconnectDelay(initialReconnectDelay);
            settings.setReconnectDelay(reconnectDelay);
            return factory.createConnectionFactory(MessagingServiceJmsConnectionFactory.class, settings);
        } catch (MessagingException e) {
            throw new IllegalStateException("Unable to create the Connection Factory", e);
        }
    }

    // -- Object

    public String toString() {
        return "[" + connectionName + "](https:" + host + (port == 443 ? "" : ":" + port) + "/protocols/amqp10ws)";
    }
}
