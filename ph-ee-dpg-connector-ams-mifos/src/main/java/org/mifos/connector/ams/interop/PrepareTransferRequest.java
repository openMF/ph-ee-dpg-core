package org.mifos.connector.ams.interop;

import static org.mifos.connector.ams.camel.config.CamelProperties.TRANSACTION_ROLE;
import static org.mifos.connector.conductor.ConductorUtil.conductorVariable;
import static org.mifos.connector.conductor.ConductorVariables.BOOK_TRANSACTION_ID;
import static org.mifos.connector.conductor.ConductorVariables.EXTERNAL_ACCOUNT_ID;
import static org.mifos.connector.conductor.ConductorVariables.NOTE;
import static org.mifos.connector.conductor.ConductorVariables.TRANSACTION_ID;
import static org.mifos.connector.conductor.ConductorVariables.TRANSFER_CODE;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.mifos.connector.common.ams.dto.TransferFspRequestDTO;
import org.mifos.connector.common.mojaloop.dto.FspMoneyData;
import org.mifos.connector.common.mojaloop.dto.MoneyData;
import org.mifos.connector.common.mojaloop.dto.TransactionType;
import org.mifos.connector.common.mojaloop.type.InitiatorType;
import org.mifos.connector.common.mojaloop.type.Scenario;
import org.mifos.connector.common.mojaloop.type.TransactionRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
// @ConditionalOnExpression("${ams.local.enabled}")
public class PrepareTransferRequest implements Processor {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void process(Exchange exchange) throws Exception {
        String initiator = conductorVariable(exchange, "initiator", String.class);
        String initiatorType = conductorVariable(exchange, "initiatorType", String.class);
        String scenario = conductorVariable(exchange, "scenario", String.class);

        logger.info("Preparing transfer request for initiator: {}, initiatorType: {}, scenario: {}", initiator, initiatorType, scenario);

        TransactionType transactionType = new TransactionType();
        transactionType.setInitiator(TransactionRole.valueOf(initiator));
        transactionType.setInitiatorType(InitiatorType.valueOf(initiatorType));
        transactionType.setScenario(Scenario.valueOf(scenario));

        String note = conductorVariable(exchange, NOTE, String.class);

        MoneyData am = exchange.getProperty("amount", MoneyData.class);
        FspMoneyData amount = objectMapper.convertValue(am, FspMoneyData.class);
        FspMoneyData fspFee = conductorVariable(exchange, "fspFee", FspMoneyData.class);
        FspMoneyData fspCommission = conductorVariable(exchange, "fspCommission", FspMoneyData.class);

        String existingTransferCode = exchange.getProperty(TRANSFER_CODE, String.class);
        String transferCode;
        if (existingTransferCode != null) {
            logger.info("Existing code not null");
            transferCode = existingTransferCode;
        } else {
            logger.info("Existing code null");
            transferCode = UUID.randomUUID().toString();
            exchange.setProperty(TRANSFER_CODE, transferCode);
        }

        String transactionCode = exchange.getProperty(BOOK_TRANSACTION_ID, String.class) != null
                ? exchange.getProperty(BOOK_TRANSACTION_ID, String.class)
                : exchange.getProperty(TRANSACTION_ID, String.class);
        logger.info("using transaction code {}", transactionCode);

        TransferFspRequestDTO transferRequestDTO = null;

        if (fspFee != null || fspCommission != null) {
            transferRequestDTO = new TransferFspRequestDTO(transactionCode, transferCode,
                    exchange.getProperty(EXTERNAL_ACCOUNT_ID, String.class), amount, fspFee, fspCommission,
                    TransactionRole.valueOf(exchange.getProperty(TRANSACTION_ROLE, String.class)), transactionType, note);
        } else {
            transferRequestDTO = new TransferFspRequestDTO(transactionCode, transferCode,
                    exchange.getProperty(EXTERNAL_ACCOUNT_ID, String.class), amount,
                    TransactionRole.valueOf(exchange.getProperty(TRANSACTION_ROLE, String.class)));
        }

        logger.debug("prepared transferRequestDTO: {}", objectMapper.writeValueAsString(transferRequestDTO));
        exchange.getIn().setBody(transferRequestDTO);
    }
}
