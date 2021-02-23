package com.azure.cosmos.kafka.connect.sink;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.implementation.BadRequestException;
import com.azure.cosmos.kafka.connect.CosmosDBConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements the Kafka Task for the CosmosDB Sink Connector
 */
public class CosmosDBSinkTask extends SinkTask {
    private static final Logger logger = LoggerFactory.getLogger(CosmosDBSinkTask.class);
    private CosmosClient client = null;
    private CosmosDBSinkConfig config;
    ObjectMapper mapper = new ObjectMapper();

    @Override
    public String version() {
        return this.getClass().getPackage().getImplementationVersion();
    }

    @Override
    public void start(Map<String, String> map) {
        logger.trace("Sink task started.");
        this.config = new CosmosDBSinkConfig(map);

        this.client = new CosmosClientBuilder()
                .endpoint(config.getConnEndpoint())
                .key(config.getConnKey())
                .userAgentSuffix(CosmosDBConfig.COSMOS_CLIENT_USER_AGENT_SUFFIX + version()).buildClient();

        client.createDatabaseIfNotExists(config.getDatabaseName());
    }

    @Override
    public void put(Collection<SinkRecord> records) {
        if (CollectionUtils.isEmpty(records)) {
            logger.info("No records to be written");
            return;
        }

        logger.info("Sending {} records to be written", records.size());

        Map<String, List<SinkRecord>> recordsByContainer = records.stream()
                // Find target container for each record
                .collect(Collectors.groupingBy(record -> config.getTopicContainerMap()
                        .getContainerForTopic(record.topic())
                        .orElseThrow(() -> new IllegalStateException(
                                String.format("No container defined for topic %s .", record.topic())))));

        for (Map.Entry<String, List<SinkRecord>> entry : recordsByContainer.entrySet()) {
            String containerName = entry.getKey();
            CosmosContainer container = client.getDatabase(config.getDatabaseName()).getContainer(containerName);
            for (SinkRecord record : entry.getValue()) {
                logger.debug("Writing record, value type: {}", record.value().getClass().getName());
                logger.debug("Key Schema: {}", record.keySchema());
                logger.debug("Value schema: {}", record.valueSchema());
                logger.debug("Value.toString(): {}", record.value() != null ? record.value().toString() : "<null>");

                Object recordValue;
                if (record.value() instanceof Struct) {
                    Map<String, Object> jsonMap = StructToJsonMap.toJsonMap((Struct) record.value());
                    recordValue = mapper.convertValue(jsonMap, JsonNode.class);
                } else {
                    recordValue = record.value();
                }

                try {
                    addItemToContainer(container, recordValue);
                } catch (BadRequestException bre) {
                    throw new CosmosDBWriteException(record, bre);
                }
            }
        }
    }

    private void addItemToContainer(CosmosContainer container, Object recordValue) {
        if (Boolean.TRUE.equals(config.getUseUpsert())) {
            container.upsertItem(recordValue);
        } else {
            container.createItem(recordValue);
        }
    }

    @Override
    public void stop() {
        logger.trace("Stopping CosmosDB sink task");

        if (client != null) {
            client.close();
            client = null;
        }

        client = null;

    }
}
