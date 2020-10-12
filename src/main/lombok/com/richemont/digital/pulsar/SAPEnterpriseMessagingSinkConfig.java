package com.richemont.digital.pulsar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Preconditions;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.apache.pulsar.io.core.annotations.FieldDoc;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class SAPEnterpriseMessagingSinkConfig
        extends SAPEnterpriseMessagingContext {

    private static final long serialVersionUID = 1L;

    @FieldDoc(
            required = true,
            defaultValue = "",
            help = "The exchange to publish the messages on")
    private String exchangeName;

    @FieldDoc(
            required = false,
            defaultValue = "",
            help = "The routing key used for publishing the messages")
    private String routingKey;

    @FieldDoc(
            required = false,
            defaultValue = "topic",
            help = "The exchange type to publish the messages on")
    private String exchangeType = "topic";

    public static SAPEnterpriseMessagingSinkConfig load(String yamlFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return mapper.readValue(new File(yamlFile), SAPEnterpriseMessagingSinkConfig.class);
    }

    public static SAPEnterpriseMessagingSinkConfig load(Map<String, Object> map) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(new ObjectMapper().writeValueAsString(map), SAPEnterpriseMessagingSinkConfig.class);
    }

    @Override
    public void validate() {
        super.validate();
        Preconditions.checkNotNull(exchangeName, "exchangeName property not set.");
    }
}
