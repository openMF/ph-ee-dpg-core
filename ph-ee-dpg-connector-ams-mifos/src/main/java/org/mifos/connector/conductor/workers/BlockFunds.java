package org.mifos.connector.conductor.workers;

import static org.mifos.connector.ams.camel.config.CamelProperties.PROCESS_TYPE;
import static org.mifos.connector.ams.camel.config.CamelProperties.TRANSACTION_ROLE;
import static org.mifos.connector.ams.camel.config.CamelProperties.TRANSFER_ACTION;
import static org.mifos.connector.ams.camel.config.CamelProperties._JOB_KEY;
import static org.mifos.connector.common.ams.dto.TransferActionType.PREPARE;
import static org.mifos.connector.conductor.ConductorUtil.conductorVariablesToCamelProperties;
import static org.mifos.connector.conductor.ConductorVariables.CHANNEL_REQUEST;
import static org.mifos.connector.conductor.ConductorVariables.EXTERNAL_ACCOUNT_ID;
import static org.mifos.connector.conductor.ConductorVariables.LOCAL_QUOTE_RESPONSE;
import static org.mifos.connector.conductor.ConductorVariables.PARTY_ID;
import static org.mifos.connector.conductor.ConductorVariables.PARTY_ID_TYPE;
import static org.mifos.connector.conductor.ConductorVariables.TENANT_ID;
import static org.mifos.connector.conductor.ConductorVariables.TRANSACTION_ID;
import static org.mifos.connector.conductor.ConductorVariables.TRANSFER_PREPARE_FAILED;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import java.util.HashMap;
import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.DefaultExchange;
import org.mifos.connector.common.channel.dto.TransactionChannelRequestDTO;
import org.mifos.connector.common.mojaloop.type.TransactionRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BlockFunds implements Worker {

    String taskDefName;

    @Value("${ams.local.enabled}")
    private boolean isAmsLocalEnabled;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    @Override
    public String getTaskDefName() {
        return taskDefName;
    }

    public void setTaskDefName(String taskDefName) {
        this.taskDefName = taskDefName;
    }

    @Override
    public TaskResult execute(Task task) {

        logger.info("********** worker execution started *********{}*********", isAmsLocalEnabled);

        Map<String, Object> map = task.getInputData();

        TaskResult result = new TaskResult(task);

        if (isAmsLocalEnabled) {
            Exchange ex = new DefaultExchange(camelContext);
            conductorVariablesToCamelProperties(map, ex, TRANSACTION_ID, CHANNEL_REQUEST, EXTERNAL_ACCOUNT_ID, TENANT_ID,
                    LOCAL_QUOTE_RESPONSE, PROCESS_TYPE);
            ex.setProperty("transactionId", task.getWorkflowInstanceId());
            ex.setProperty(PROCESS_TYPE, "api");
            TransactionChannelRequestDTO channelRequest = null;
            try {
                channelRequest = objectMapper.readValue(ex.getProperty(CHANNEL_REQUEST, String.class), TransactionChannelRequestDTO.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            ex.setProperty(PARTY_ID_TYPE, channelRequest.getPayer().getPartyIdInfo().getPartyIdType().name());
            ex.setProperty(PARTY_ID, channelRequest.getPayer().getPartyIdInfo().getPartyIdentifier());
            ex.setProperty(TRANSFER_ACTION, PREPARE.name());
            ex.setProperty(_JOB_KEY, task.getWorkflowInstanceId());
            ex.setProperty(TRANSACTION_ROLE, TransactionRole.PAYER.name());
            ex.setProperty("payeeTenantId", map.get("payeeTenantId"));
            ex.setProperty("amount", channelRequest.getAmount());
            logger.debug("Payee Id before block funds {}", map.get("payeeTenantId"));
            producerTemplate.send("direct:send-transfers", ex);
            logger.info("variable {}", map);
            logger.info("Output Data {}", ex.getProperty("outputData"));
            result.setOutputData((Map<String, Object>) ex.getProperty("outputData"));
            if (ex.getProperty("outputData") == null) {
                result.setStatus(TaskResult.Status.FAILED);
            } else {
                result.setStatus(TaskResult.Status.COMPLETED);
            }

        } else {
            Map<String, Object> variables = new HashMap<>();
            variables.put(TRANSFER_PREPARE_FAILED, false);

            result.setOutputData(variables);
            result.setStatus(TaskResult.Status.COMPLETED);
        }

        return result;
    }
}
