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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Preconditions;
import com.sap.cloud.servicesdk.xbem.core.MessagingService;
import com.sap.cloud.servicesdk.xbem.core.MessagingServiceFactory;
import com.sap.cloud.servicesdk.xbem.core.exception.MessagingException;
import com.sap.cloud.servicesdk.xbem.core.impl.MessagingServiceFactoryCreator;
import com.sap.cloud.servicesdk.xbem.extension.sapcp.jms.MessagingServiceJmsConnectionFactory;
import com.sap.cloud.servicesdk.xbem.extension.sapcp.jms.MessagingServiceJmsSettings;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.pulsar.io.core.annotations.FieldDoc;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

/**
 * Configuration object for all SAPEnterpriseMessaging components.
 */
@Data
@Accessors(chain = true)
public class SAPEnterpriseMessagingConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @FieldDoc(
            required = true,
            defaultValue = "",
            help = "The connection name used for connecting to SAPEnterpriseMessaging.")
    private String connectionName;

    @FieldDoc(
            required = true,
            defaultValue = "",
            help = "SAP HANA XS application name.")
    private String xsappname;

    @FieldDoc(
            required = true,
            defaultValue = "",
            help = "OAuth2 client id.")
    private String clientID;

    @FieldDoc(
            required = true,
            defaultValue = "",
            help = "OAuth2 client secret.")
    private String clientSecret;

    @FieldDoc(
            required = true,
            defaultValue = "",
            help = "OAuth2 token endpoint URL.")
    private String tokenEndpoint;

    @FieldDoc(
            required = true,
            defaultValue = "",
            help = "SAP Enterprise Messaging Service URL.")
    private String serviceURL;

    @FieldDoc(
            required = true,
            defaultValue = "amqp10ws",
            help = "SAP Enterprise Messaging protocol.")
    private String protocol = "amqp10ws";

    @FieldDoc(
            required = true,
            defaultValue = "queue:destination",
            help = "The SAPEnterpriseMessaging destination name optionally prefixed with 'topic:'; if not prefixed 'queue:' is assumed.")
    private String destination;

    @FieldDoc(
            required = false,
            defaultValue = "20",
            help = "Maximum number of attempts at reconnecting before giving up; -1 for unlimited retries.")
    private int maxReconnectAttempts= 20;

    @FieldDoc(
            required = false,
            defaultValue = "3000",
            help = "Delay in millis before reconnecting after the first failure.")
    private int initialReconnectDelay= 3000;

    @FieldDoc(
            required = false,
            defaultValue = "5000",
            help = "Delay in millis between reeconnect attempts after the first.")
    private int reconnectDelay= 5000;

    public void validate() {
        Preconditions.checkNotNull(connectionName, "connectionName property not set.");
        Preconditions.checkNotNull(clientID, "clientID property not set.");
        Preconditions.checkNotNull(clientSecret, "clientSecret property not set.");
        Preconditions.checkNotNull(tokenEndpoint, "clientSecret property not set.");
        Preconditions.checkNotNull(protocol, "protocol property not set.");
        Preconditions.checkNotNull(serviceURL, "serviceURL property not set.");
        Preconditions.checkNotNull(xsappname, "xsappname property not set.");
        Preconditions.checkNotNull(destination, "destination property not set.");
    }

    String getJMSDestination() {
        return destination.contains(":") ? destination : "queue:" + destination;
    }

    MessagingServiceJmsConnectionFactory getMessagingServiceJmsConnectionFactory() {
//        Cloud cloud = new CloudFactory().getCloud();
//        MessagingService service = cloud.getSingletonServiceConnector(MessagingService.class, null);
//        if (service == null) {
//            throw new IllegalStateException("Unable to create the MessagingService.");
//        }

        MessagingService service = new MessagingService();
        service.setServiceUrl(serviceURL);
        service.setClientId(clientID);
        service.setClientSecret(clientSecret);
        service.setOAuthTokenEndpoint(tokenEndpoint);
        service.setProtocol(protocol);
        service.setXsappname(xsappname);

        // service.setSubdomain("");

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

    public static SAPEnterpriseMessagingConfig load(String yamlFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return mapper.readValue(new File(yamlFile), SAPEnterpriseMessagingConfig.class);
    }

    public static SAPEnterpriseMessagingConfig load(Map<String, Object> map) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(new ObjectMapper().writeValueAsString(map), SAPEnterpriseMessagingConfig.class);
    }


    // -- Object

    public String toString() {
        return "[" + connectionName + "](" + getJMSDestination() + ")";
    }
}
