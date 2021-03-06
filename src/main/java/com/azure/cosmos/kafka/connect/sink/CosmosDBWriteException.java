package com.azure.cosmos.kafka.connect.sink;

import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.sink.SinkRecord;

/**
 * Exception thrown when an attempt to write a message to CosmosDB has failed.
 */
public class CosmosDBWriteException extends ConnectException {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public CosmosDBWriteException(SinkRecord record, Throwable cause) {
        super("Unable to write record to CosmosDB: " + record.key() + " (value schema:" + record.valueSchema(), cause);
    }
}
