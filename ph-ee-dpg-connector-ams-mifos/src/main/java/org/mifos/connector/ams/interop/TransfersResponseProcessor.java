package org.mifos.connector.ams.interop;

import static org.mifos.connector.ams.camel.config.CamelProperties.TRANSFER_ACTION;
import static org.mifos.connector.ams.camel.config.CamelProperties._JOB_KEY;
import static org.mifos.connector.common.ams.dto.TransferActionType.PREPARE;
import static org.mifos.connector.conductor.ConductorVariables.ACTION_FAILURE_MAP;
import static org.mifos.connector.conductor.ConductorVariables.ERROR_CODE;
import static org.mifos.connector.conductor.ConductorVariables.ERROR_INFORMATION;
import static org.mifos.connector.conductor.ConductorVariables.ERROR_PAYLOAD;
import static org.mifos.connector.conductor.ConductorVariables.TRANSFER_CODE;
import static org.mifos.connector.conductor.ConductorVariables.TRANSFER_CREATE_FAILED;
import static org.mifos.connector.conductor.ConductorVariables.TRANSFER_RESPONSE_PREFIX;

import java.util.HashMap;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.mifos.connector.ams.errorhandler.ErrorTranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
// @ConditionalOnExpression("${ams.local.enabled}")
public class TransfersResponseProcessor implements Processor {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ErrorTranslator errorTranslator;

    @Override
    public void process(Exchange exchange) {
        Integer responseCode = exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        String transferAction = exchange.getProperty(TRANSFER_ACTION, String.class);

        Map<String, Object> variables = new HashMap<>();
        variables.put(TRANSFER_CREATE_FAILED, responseCode > 202);

        if (responseCode > 202) {

            variables.put(ERROR_CODE, exchange.getProperty(ERROR_CODE));
            variables.put(ERROR_INFORMATION, exchange.getProperty(ERROR_INFORMATION));
            variables.put(ERROR_PAYLOAD, exchange.getProperty(ERROR_PAYLOAD));
            variables = errorTranslator.translateError(variables);

            variables.put(ACTION_FAILURE_MAP.get(transferAction), true);

        } else {
            variables.put(TRANSFER_RESPONSE_PREFIX + "-" + transferAction, exchange.getIn().getBody());
            if (PREPARE.name().equals(transferAction)) {
                variables.put(TRANSFER_CODE, exchange.getProperty(TRANSFER_CODE));
            }
            variables.put(ACTION_FAILURE_MAP.get(transferAction), false);
        }

        exchange.setProperty("outputData", variables);
        logger.info("Completed job with key: {}", exchange.getProperty(_JOB_KEY, String.class));
    }
}
