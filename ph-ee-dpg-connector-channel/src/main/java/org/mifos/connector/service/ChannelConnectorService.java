package org.mifos.connector.service;

import static org.mifos.connector.common.mojaloop.type.InitiatorType.CONSUMER;
import static org.mifos.connector.common.mojaloop.type.Scenario.TRANSFER;
import static org.mifos.connector.common.mojaloop.type.TransactionRole.PAYER;
import static org.mifos.connector.conductor.ConductorVariables.IS_RTP_REQUEST;
import static org.mifos.connector.conductor.ConductorVariables.TENANT_ID;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.mifos.connector.channel.properties.TenantImplementation;
import org.mifos.connector.channel.properties.TenantImplementationProperties;
import org.mifos.connector.channel.utils.Headers;
import org.mifos.connector.common.channel.dto.TransactionChannelRequestDTO;
import org.mifos.connector.common.mojaloop.dto.FspMoneyData;
import org.mifos.connector.common.mojaloop.dto.TransactionType;
import org.mifos.connector.conductor.ConductorWorkflowStarter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChannelConnectorService {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    ConductorWorkflowStarter conductorWorkflowStarter;

    @Autowired
    TenantImplementationProperties tenantImplementationProperties;

    @Autowired
    ObjectMapper objectMapper;

    public static final String BATCH_ID = "batchId";

    private String paymentTransferFlow;
    private String specialPaymentTransferFlow;
    private List<String> dfspIds;
    String destinationDfspId;

    public ChannelConnectorService(@Value("#{'${dfspids}'.split(',')}") List<String> dfspIds,
            @Value("${bpmn.flows.payment-transfer}") String paymentTransferFlow,
            @Value("${bpmn.flows.special-payment-transfer}") String specialPaymentTransferFlow,
            @Value("${destination.dfspid}") String destinationDfspId, ObjectMapper objectMapper) {
        this.paymentTransferFlow = paymentTransferFlow;
        this.specialPaymentTransferFlow = specialPaymentTransferFlow;
        this.dfspIds = dfspIds;
        this.destinationDfspId = destinationDfspId;
    }

    public String processTransferRequest(String requestBody, Headers headers) throws Exception {

        Map<String, Object> extraVariables = new HashMap<>();
        extraVariables.put(IS_RTP_REQUEST, false);

        // adding batchId zeebeVariable form header
        String batchIdHeader = headers.get(BATCH_ID) != null ? headers.get(BATCH_ID).toString() : "";
        extraVariables.put(BATCH_ID, batchIdHeader);

        String tenantId = headers.get("Platform-TenantId").toString();
        String clientCorrelationId = headers.get("X-CorrelationID").toString();
        if (tenantId == null || !dfspIds.contains(tenantId)) {
            throw new RuntimeException("Requested tenant " + tenantId + " not configured in the connector!");
        }
        extraVariables.put(TENANT_ID, tenantId);

        TransactionChannelRequestDTO channelRequest = objectMapper.readValue(requestBody, TransactionChannelRequestDTO.class);
        TransactionType transactionType = new TransactionType();
        transactionType.setInitiator(PAYER);
        transactionType.setInitiatorType(CONSUMER);
        transactionType.setScenario(TRANSFER);
        channelRequest.setTransactionType(transactionType);
        channelRequest.getPayer().getPartyIdInfo().setFspId(destinationDfspId);
        String customDataString = String.valueOf(channelRequest.getCustomData());
        String currency = channelRequest.getAmount().getCurrency();

        extraVariables.put("customData", customDataString);
        extraVariables.put("currency", currency);
        extraVariables.put("initiator", transactionType.getInitiator().name());
        extraVariables.put("initiatorType", transactionType.getInitiatorType().name());
        extraVariables.put("scenario", transactionType.getScenario().name());
        extraVariables.put("amount",
                new FspMoneyData(channelRequest.getAmount().getAmountDecimal(), channelRequest.getAmount().getCurrency()));
        extraVariables.put("clientCorrelationId", clientCorrelationId);
        extraVariables.put("initiatorFspId", channelRequest.getPayer().getPartyIdInfo().getFspId());
        String tenantSpecificBpmn;
        String bpmn = getWorkflowForTenant(tenantId, "payment-transfer");
        if (channelRequest.getPayer().getPartyIdInfo().getPartyIdentifier().startsWith("6666")) {
            tenantSpecificBpmn = bpmn.equals("default") ? specialPaymentTransferFlow.replace("{dfspid}", tenantId)
                    : bpmn.replace("{dfspid}", tenantId);
            extraVariables.put("specialTermination", true);
        } else {
            tenantSpecificBpmn = bpmn.equals("default") ? paymentTransferFlow.replace("{dfspid}", tenantId)
                    : bpmn.replace("{dfspid}", tenantId);
            extraVariables.put("specialTermination", false);
        }

        String transactionId = conductorWorkflowStarter.startWorkflow(tenantSpecificBpmn, objectMapper.writeValueAsString(channelRequest),
                extraVariables);

        JSONObject response = new JSONObject();
        response.put("transactionId", transactionId);
        return response.toString();
    }

    public String getWorkflowForTenant(String tenantId, String useCase) {

        for (TenantImplementation tenant : tenantImplementationProperties.getTenants()) {
            if (tenant.getId().equals(tenantId)) {
                return tenant.getFlows().getOrDefault(useCase, "default");
            }
        }
        return "default";
    }
}
