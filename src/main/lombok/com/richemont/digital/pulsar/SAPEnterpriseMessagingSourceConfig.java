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
import java.io.Serializable;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class SAPEnterpriseMessagingSourceConfig extends SAPEnterpriseMessagingAbstractConfig {

    private static final long serialVersionUID = 1L;

    @FieldDoc(
            required = true,
            defaultValue = "",
            help = "The RabbitMQ queue name from which messages should be read from or written to")
    private String queueName;

    @FieldDoc(
            required = false,
            defaultValue = "0",
            help = "Maximum number of messages that the server will deliver, 0 for unlimited")
    private int prefetchCount = 0;

    @FieldDoc(
            required = false,
            defaultValue = "false",
            help = "Set true if the settings should be applied to the entire channel rather than each consumer")
    private boolean prefetchGlobal = false;

    public static SAPEnterpriseMessagingSourceConfig load(String yamlFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return mapper.readValue(new File(yamlFile), SAPEnterpriseMessagingSourceConfig.class);
    }

    public static SAPEnterpriseMessagingSourceConfig load(Map<String, Object> map) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(new ObjectMapper().writeValueAsString(map), SAPEnterpriseMessagingSourceConfig.class);
    }

    @Override
    public void validate() {
        super.validate();
        Preconditions.checkNotNull(queueName, "queueName property not set.");
        Preconditions.checkArgument(prefetchCount >= 0, "prefetchCount must be non-negative.");
    }
}
