package org.mifos.pheedpgimporterrdbms.config;

import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;

public class CustomStreamsExceptionHandler implements StreamsUncaughtExceptionHandler {

    @Override
    public StreamThreadExceptionResponse handle(Throwable exception) {
        return null;
    }
}
