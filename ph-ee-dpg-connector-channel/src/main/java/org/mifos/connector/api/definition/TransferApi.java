package org.mifos.connector.api.definition;

import org.mifos.connector.dto.GsmaP2PResponseDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

public interface TransferApi {

    String CLIENTCORRELATIONID = "X-CorrelationID";
    String BATCH_ID_HEADER = "X-BatchID";

    @PostMapping("/channel/transfer")
    GsmaP2PResponseDto transfer(@RequestHeader(value = "Platform-TenantId") String tenant,
            @RequestHeader(value = BATCH_ID_HEADER, required = false) String batchId,
            @RequestHeader(value = CLIENTCORRELATIONID, required = false) String correlationId, @RequestBody Object requestBody)
            throws Exception;
}
