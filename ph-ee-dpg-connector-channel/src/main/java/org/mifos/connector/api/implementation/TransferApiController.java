package org.mifos.connector.api.implementation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mifos.connector.api.definition.TransferApi;
import org.mifos.connector.channel.utils.Headers;
import org.mifos.connector.dto.GsmaP2PResponseDto;
import org.mifos.connector.service.ChannelConnectorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransferApiController implements TransferApi {

    @Autowired
    ChannelConnectorService channelConnectorService;

    @Autowired
    ObjectMapper objectMapper;

    public static final String BATCH_ID = "batchId";
    public static final String CLIENTCORRELATIONID = "X-CorrelationID";

    @Override
    public GsmaP2PResponseDto transfer(String tenant, String batchId, String correlationId, Object requestBody) throws Exception {
        Headers headers = new Headers.HeaderBuilder().addHeader("Platform-TenantId", tenant).addHeader(BATCH_ID, batchId)
                .addHeader(CLIENTCORRELATIONID, correlationId).build();
        String responseBody = channelConnectorService.processTransferRequest(objectMapper.writeValueAsString(requestBody), headers);

        return objectMapper.readValue(responseBody, GsmaP2PResponseDto.class);
    }
}
