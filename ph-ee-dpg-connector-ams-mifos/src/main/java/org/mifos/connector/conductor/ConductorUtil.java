package org.mifos.connector.conductor;

import static org.mifos.connector.ams.camel.config.CamelProperties.TRANSFER_ACTION;
import static org.mifos.connector.common.ams.dto.TransferActionType.CREATE;
import static org.mifos.connector.conductor.ConductorVariables.ACCOUNT_IDENTIFIER;
import static org.mifos.connector.conductor.ConductorVariables.ACCOUNT_NUMBER;
import static org.mifos.connector.conductor.ConductorVariables.CHANNEL_REQUEST;
import static org.mifos.connector.conductor.ConductorVariables.EXTERNAL_ACCOUNT_ID;
import static org.mifos.connector.conductor.ConductorVariables.REQUESTED_DATE;
import static org.mifos.connector.conductor.ConductorVariables.TENANT_ID;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.apache.camel.Exchange;
import org.mifos.connector.common.ams.dto.LoanRepaymentDTO;
import org.mifos.connector.common.gsma.dto.CustomData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConductorUtil {

    private static Logger logger = LoggerFactory.getLogger(ConductorUtil.class);
    private static ObjectMapper objectMapper = new ObjectMapper();
    @Value("#{'${accountPrefixes}'.split(',')}")
    public List<String> accountPrefixes;

    public static void conductorVariablesToCamelProperties(Map<String, Object> variables, Exchange exchange, String... names) {
        exchange.setProperty("conductorVariables", variables);

        for (String name : names) {
            Object value = variables.get(name);
            if (value == null) {
                logger.error("failed to find  variable name {}", name);
            } else {
                exchange.setProperty(name, value);
            }
        }
    }

    public static Map<String, Object> conductorVariablesFrom(Exchange exchange) {
        return exchange.getProperty("conductorVariables", Map.class);
    }

    public static <T> T conductorVariable(Exchange exchange, String name, Class<T> clazz) throws Exception {
        Object content = conductorVariablesFrom(exchange).get(name);
        if (content instanceof Map) {
            return objectMapper.readValue(objectMapper.writeValueAsString(content), clazz);
        }
        return (T) content;
    }

    public static String getCurrentDate(String requestedDate, String dateFormat) {
        String dateFormatGiven = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
        LocalDateTime datetime = LocalDateTime.parse(requestedDate, DateTimeFormatter.ofPattern(dateFormatGiven));
        String newDate = datetime.format(DateTimeFormatter.ofPattern(dateFormat));
        return newDate;
    }

    public static LoanRepaymentDTO setLoanRepaymentBody(Exchange exchange) {
        String dateFormat = "dd MMMM yyyy";
        LoanRepaymentDTO loanRepaymentDTO = new LoanRepaymentDTO();
        loanRepaymentDTO.setDateFormat(dateFormat);
        loanRepaymentDTO.setLocale("en");
        loanRepaymentDTO.setTransactionAmount(exchange.getProperty("amount").toString());
        loanRepaymentDTO.setTransactionDate(exchange.getProperty("requestedDate").toString());
        return loanRepaymentDTO;
    }

    public static void setconductorVariables(Exchange e, Map<String, Object> variables, String requestDate,
            String accountHoldingInstitutionId, String transactionChannelRequestDTO) {
        String dateFormat = "dd MMMM yyyy";
        String currentDate = getCurrentDate(requestDate, dateFormat);

        variables.put(ACCOUNT_IDENTIFIER, e.getProperty(ACCOUNT_IDENTIFIER));
        variables.put(ACCOUNT_NUMBER, e.getProperty(ACCOUNT_NUMBER));
        // variables.put(TRANSACTION_ID, UUID.randomUUID().toString());
        variables.put(TENANT_ID, accountHoldingInstitutionId);
        variables.put(TRANSFER_ACTION, CREATE.name());
        variables.put(CHANNEL_REQUEST, transactionChannelRequestDTO);
        variables.put(REQUESTED_DATE, currentDate);
        variables.put(EXTERNAL_ACCOUNT_ID, e.getProperty(ACCOUNT_NUMBER));
        variables.put("payeeTenantId", accountHoldingInstitutionId);
    }

    public Exchange setAccountTypeAndNumber(Exchange e, String accountNo) {
        String accountTypeIdentifier = "";
        String accountNumber = "";
        int accountNoLength = accountNo.length();
        // Separating account id and prefix
        for (String accountPrefix : accountPrefixes) {
            if (accountNo.startsWith(accountPrefix)) {
                accountNumber = accountNo.substring(accountPrefix.length(), accountNoLength);
                accountTypeIdentifier = accountNo.substring(0, accountPrefix.length());
                break;
            }
        }
        logger.debug("Accout number:{}, Identifier:{}", accountNumber, accountTypeIdentifier);
        e.setProperty(ACCOUNT_NUMBER, accountNumber);
        e.setProperty(ACCOUNT_IDENTIFIER, accountTypeIdentifier);
        return e;
    }

    public static String getValueofKey(List<CustomData> customData, String key) {
        for (CustomData obj : customData) {
            String keyString = obj.getKey();
            if (keyString.equalsIgnoreCase(key)) {
                return obj.getValue().toString();
            }
        }
        return null;
    }
}
