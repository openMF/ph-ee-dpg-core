package org.mifos.connector.ams.errorhandler;

import static org.mifos.connector.conductor.ConductorVariables.ERROR_CODE;
import static org.mifos.connector.conductor.ConductorVariables.ERROR_INFORMATION;
import static org.mifos.connector.conductor.ConductorVariables.ERROR_PAYLOAD;
import static org.mifos.connector.conductor.ConductorVariables.IS_ERROR_HANDLED;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.mifos.connector.ams.interop.errordto.ErrorResponse;
import org.mifos.connector.common.channel.dto.PhErrorDTO;
import org.mifos.connector.common.exception.PaymentHubError;
import org.mifos.connector.common.exception.mapper.ErrorMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ErrorTranslator {

    @Autowired
    private ErrorMapper errorMapper;

    @Autowired
    ObjectMapper objectMapper;

    public Map<String, Object> translateError(Map<String, Object> FinalVariables) {
        checkIfErrorIsAlreadyMappedToInternal(FinalVariables);
        String errorCode = (String) FinalVariables.get(ERROR_CODE);
        if (errorCode == null) {
            return FinalVariables;
        }

        PaymentHubError paymentHubError;
        // todo improve try catch logic
        try {
            paymentHubError = errorMapper.getInternalError(errorCode);
            if (paymentHubError == null) {
                throw new NullPointerException();
            }
        } catch (Exception e) {
            try {
                paymentHubError = PaymentHubError.fromCode(errorCode);
            } catch (Exception exc) {
                return FinalVariables;
            }
        }

        PhErrorDTO phErrorDTO;
        try {
            ErrorResponse externalErrorObject = (ErrorResponse) FinalVariables.get(ERROR_PAYLOAD);
            phErrorDTO = new PhErrorDTO.PhErrorDTOBuilder(paymentHubError)
                    .developerMessage(objectMapper.writeValueAsString(externalErrorObject))
                    .defaultUserMessage((String) FinalVariables.get(ERROR_INFORMATION)).build();
        } catch (Exception e) {
            phErrorDTO = new PhErrorDTO.PhErrorDTOBuilder(paymentHubError).developerMessage((String) FinalVariables.get(ERROR_PAYLOAD))
                    .defaultUserMessage((String) FinalVariables.get(ERROR_INFORMATION)).build();
        }

        FinalVariables.put(ERROR_CODE, phErrorDTO.getErrorCode());
        FinalVariables.put(ERROR_INFORMATION, phErrorDTO.getErrorDescription());
        try {
            FinalVariables.put(ERROR_INFORMATION, objectMapper.writeValueAsString(phErrorDTO));
            PhErrorDTO errorDTO = objectMapper.readValue((String) FinalVariables.get(ERROR_INFORMATION), PhErrorDTO.class);
        } catch (JsonProcessingException e) {
            FinalVariables.put(ERROR_INFORMATION, phErrorDTO.toString());
        }

        return FinalVariables;
    }

    private void checkIfErrorIsAlreadyMappedToInternal(Map<String, Object> FinalVariables) {
        boolean isErrorHandled;
        try {
            isErrorHandled = (boolean) FinalVariables.get(IS_ERROR_HANDLED);
        } catch (Exception e) {
            isErrorHandled = false;
        }
        if (!isErrorHandled) {
            FinalVariables.put(IS_ERROR_HANDLED, true);
        }
    }
}
