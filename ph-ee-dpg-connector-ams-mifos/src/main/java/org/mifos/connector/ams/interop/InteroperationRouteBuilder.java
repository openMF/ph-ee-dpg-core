package org.mifos.connector.ams.interop;

import static org.mifos.connector.ams.camel.config.CamelProperties.IS_ERROR_SET_MANUALLY;
import static org.mifos.connector.ams.camel.config.CamelProperties.PROCESS_TYPE;
import static org.mifos.connector.ams.camel.config.CamelProperties.TRANSFER_ACTION;
import static org.mifos.connector.conductor.ConductorVariables.ERROR_CODE;
import static org.mifos.connector.conductor.ConductorVariables.ERROR_INFORMATION;
import static org.mifos.connector.conductor.ConductorVariables.ERROR_PAYLOAD;
import static org.mifos.connector.conductor.ConductorVariables.EXTERNAL_ACCOUNT_ID;
import static org.mifos.connector.conductor.ConductorVariables.PARTY_ID;
import static org.mifos.connector.conductor.ConductorVariables.PARTY_ID_TYPE;
import static org.mifos.connector.conductor.ConductorVariables.TRANSACTION_ID;
import static org.mifos.connector.conductor.ConductorVariables.TRANSFER_CODE;
import static org.mifos.connector.conductor.ConductorVariables.TRANSFER_CREATE_FAILED;
import static org.mifos.connector.conductor.ConductorVariables.TRANSFER_PREPARE_FAILED;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.json.JSONObject;
import org.mifos.connector.ams.errorhandler.ErrorTranslator;
import org.mifos.connector.ams.tenant.TenantNotExistException;
import org.mifos.connector.ams.utils.Utils;
import org.mifos.connector.common.ams.dto.PartyFspResponseDTO;
import org.mifos.connector.common.camel.ErrorHandlerRouteBuilder;
import org.mifos.connector.common.exception.PaymentHubError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
// @ConditionalOnExpression("${ams.local.enabled}")
public class InteroperationRouteBuilder extends ErrorHandlerRouteBuilder {

    @Value("${ams.local.version}")
    private String amsVersion;

    @Autowired
    private Processor pojoToString;

    @Autowired
    private AmsService amsService;

    @Autowired
    private PrepareTransferRequest prepareTransferRequest;

    @Autowired
    private TransfersResponseProcessor transfersResponseProcessor;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ErrorTranslator errorTranslator;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    public InteroperationRouteBuilder() {
        super.configure();
    }

    @Override
    public void configure() {
        onException(TenantNotExistException.class).process(e -> {
            e.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
            Exception exception = e.getException();
            if (exception != null) {
                e.getIn().setBody(exception.getMessage());
            }
        }).stop();

        from("direct:get-external-account").id("get-external-account")
                .log(LoggingLevel.INFO,
                        "Get externalAccount with identifierType: ${exchangeProperty." + PARTY_ID_TYPE + "} with value: ${exchangeProperty."
                                + PARTY_ID + "}")
                // .process(amsService::getExternalAccount)
                .process(exchange -> {
                    try {
                        amsService.getExternalAccount(exchange);
                    } catch (TenantNotExistException e) {
                        exchange.setProperty(ERROR_CODE, PaymentHubError.PayeeFspNotConfigured.getErrorCode());
                        exchange.setProperty(ERROR_INFORMATION, PaymentHubError.PayeeFspNotConfigured.getErrorDescription());
                        exchange.setProperty(ERROR_PAYLOAD, PaymentHubError.PayeeFspNotConfigured.getErrorDescription());
                        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
                        exchange.setProperty(IS_ERROR_SET_MANUALLY, true);
                    }
                }).log("Response body from get-external-account").choice()
                // check if http status code is <= 202
                .when(e -> e.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class) <= 202).unmarshal()
                .json(JsonLibrary.Jackson, PartyFspResponseDTO.class)
                .process(e -> e.setProperty(EXTERNAL_ACCOUNT_ID, e.getIn().getBody(PartyFspResponseDTO.class).getAccountId()))
                .process(exchange -> {
                    PartyFspResponseDTO dto = exchange.getIn().getBody(PartyFspResponseDTO.class);

                    logger.info("Account Id: {}", dto.getAccountId());
                    logger.info("Http response code: {}", exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class));
                }).when(e -> e.getProperty(IS_ERROR_SET_MANUALLY, Boolean.class) == null
                        || !e.getProperty(IS_ERROR_SET_MANUALLY, Boolean.class))
                .to("direct:error-handler").otherwise().endChoice();

        from("direct:send-transfers").id("send-transfers")
                .log(LoggingLevel.INFO,
                        "Sending transfer with action: ${exchangeProperty." + TRANSFER_ACTION + "} "
                                + " for transaction: ${exchangeProperty." + TRANSACTION_ID + "}")
                .to("direct:get-external-account").process(prepareTransferRequest).process(pojoToString).process(amsService::sendTransfer)
                // .to("direct:error-handler") // this route will parse and set error field if exist
                .log("Process type: ${exchangeProperty." + PROCESS_TYPE + "}").choice()
                .when(exchange -> exchange.getProperty(PROCESS_TYPE) != null && exchange.getProperty(PROCESS_TYPE).equals("api"))
                .process(exchange -> {
                    int statusCode = exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
                    logger.info(String.valueOf(exchange.getIn()));
                    if (statusCode > 202) {
                        Map<String, Object> variables = Utils.getDefaultErrorVariable(exchange, errorTranslator);
                        exchange.setProperty("outputData", variables);

                        logger.error("{}", variables.get(ERROR_INFORMATION));
                    } else {
                        Map<String, Object> variables = new HashMap<>();
                        JSONObject responseJson = new JSONObject(exchange.getIn().getBody(String.class));
                        variables.put(TRANSFER_PREPARE_FAILED, false);
                        variables.put(TRANSFER_CREATE_FAILED, false);
                        variables.put("payeeTenantId", exchange.getProperty("payeeTenantId"));
                        variables.put(TRANSFER_CODE, responseJson.getString("transferCode"));
                        logger.info("API call successful. Response Body: " + exchange.getIn().getBody(String.class));
                        exchange.setProperty("outputData", variables);
                    }
                    logger.info("End of process in send-transfers");
                }).otherwise().process(transfersResponseProcessor).end();
    }
}
