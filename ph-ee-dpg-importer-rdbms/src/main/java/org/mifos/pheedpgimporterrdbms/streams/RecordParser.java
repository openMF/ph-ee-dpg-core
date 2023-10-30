package org.mifos.pheedpgimporterrdbms.streams;

import com.jayway.jsonpath.DocumentContext;
import java.io.StringReader;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.util.Strings;
import org.mifos.pheedpgimporterrdbms.config.TransferTransformerConfig;
import org.mifos.pheedpgimporterrdbms.entity.task.Task;
import org.mifos.pheedpgimporterrdbms.entity.task.TaskRepository;
import org.mifos.pheedpgimporterrdbms.entity.transfer.Transfer;
import org.mifos.pheedpgimporterrdbms.entity.transfer.TransferRepository;
import org.mifos.pheedpgimporterrdbms.entity.transfer.TransferStatus;
import org.mifos.pheedpgimporterrdbms.entity.variable.Variable;
import org.mifos.pheedpgimporterrdbms.entity.variable.VariableRepository;
import org.mifos.pheedpgimporterrdbms.importer.JsonPathReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

@Component
public class RecordParser {

    @Autowired
    private InFlightTransferManager inFlightTransferManager;

    @Autowired
    TransferRepository transferRepository;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    VariableRepository variableRepository;

    @Autowired
    TransferTransformerConfig transferTransformerConfig;

    private final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();

    private final XPathFactory pathFactory = XPathFactory.newInstance();

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Transactional
    public List<Object> processTask(DocumentContext recordDocument, String bpmn, String flowType, String workflowInstanceKey) {
        logger.info("Processing Task instance");
        String taskStatus = recordDocument.read("$.status", String.class);
        Optional<TransferTransformerConfig.Flow> config = transferTransformerConfig.findFlow(bpmn);

        List<TransferTransformerConfig.Transformer> constantTransformers = transferTransformerConfig.getFlows().stream()
                .filter(it -> bpmn.equalsIgnoreCase(it.getName())).flatMap(it -> it.getTransformers().stream())
                .filter(it -> Strings.isNotBlank(it.getConstant())).toList();

        if ("TRANSFER".equalsIgnoreCase(flowType)) {
            logger.info("Processing flow of type TRANSFER");

            if ("SCHEDULED".equals(taskStatus)) {

                logger.info("found {} constant transformers for flow start {}", constantTransformers.size(), bpmn);
                Long scheduledTime = recordDocument.read("$.scheduledTime");

                List<Object> entities = processTask(recordDocument, workflowInstanceKey, "Task", scheduledTime);
                if (entities.size() != 0) {
                    logger.info("Saving {} entities", entities.size());
                    entities.forEach(entity -> {
                        taskRepository.save((Task) entity);
                    });
                }

                Map<String, Object> inputDataMap = recordDocument.read("$.inputData");
                inputDataMap.forEach((key, value) -> {
                    if (value == null) {
                        value = "";
                    }
                    List<Object> variables = processVariable(key, value.toString(), bpmn, workflowInstanceKey, scheduledTime, flowType);
                    logger.info("###### {} ###### {} ######", key, value);
                    variables.forEach(variable -> {
                        logger.info("********** {} *******", variable.toString());
                        variableRepository.save((Variable) variable);
                    });
                });

            } else if ("COMPLETED".equals(taskStatus)) {
                Map<String, Object> outputDataMap = recordDocument.read("$.outputData");
                outputDataMap.forEach((key, value) -> {
                    if (value == null) {
                        value = "";
                    }
                    processVariable(key, value.toString(), bpmn, workflowInstanceKey, recordDocument.read("$.endTime"), flowType);
                });
            }

            Transfer transfer = inFlightTransferManager.retrieveOrCreateTransfer(bpmn, workflowInstanceKey);
            transfer.setDirection(config.get().getDirection());
            constantTransformers.forEach(it -> applyTransformer(transfer, null, null, it));
            transferRepository.save(transfer);
        } else {
            logger.error("No matching flow types for the given request");
        }

        return List.of();
    }

    @Transactional
    public List<Object> processWorkflow(DocumentContext recordDocument, String bpmn, String flowType, String workflowInstanceKey) {
        logger.info("Processing Task instance");
        String workflowStatus = recordDocument.read("$.status", String.class);
        Optional<TransferTransformerConfig.Flow> config = transferTransformerConfig.findFlow(bpmn);

        List<TransferTransformerConfig.Transformer> constantTransformers = transferTransformerConfig.getFlows().stream()
                .filter(it -> bpmn.equalsIgnoreCase(it.getName())).flatMap(it -> it.getTransformers().stream())
                .filter(it -> Strings.isNotBlank(it.getConstant())).toList();

        if ("TRANSFER".equalsIgnoreCase(flowType)) {
            logger.info("Processing flow of type TRANSFER");
            Transfer transfer = inFlightTransferManager.retrieveOrCreateTransfer(bpmn, workflowInstanceKey);
            if ("COMPLETED".equals(workflowStatus)) {
                logger.info("finishing transfer for processInstanceKey: {}", workflowInstanceKey);
                transfer.setCompletedAt(new Date(recordDocument.read("$.endTime", Long.class)));
                transfer.setStatus(TransferStatus.COMPLETED);
            } else if ("TERMINATED".equals(workflowStatus)) {
                logger.info("terminating transfer for processInstanceKey: {}", workflowInstanceKey);
                transfer.setCompletedAt(new Date(recordDocument.read("$.endTime", Long.class)));
                transfer.setStatus(TransferStatus.TERMINATED);
            }
            transfer.setStartedAt(new Date(recordDocument.read("$.createTime", Long.class)));
            transfer.setTransactionId(workflowInstanceKey);
            transferRepository.save(transfer);
        }
        return List.of();
    }

    public List<Object> processVariable(String variableName, String variableValue, String bpmn, String workflowInstanceKey, Long timestamp,
            String flowType) {
        logger.info("Processing variable instance");
        String value = variableValue.startsWith("\"") && variableValue.endsWith("\"")
                ? StringEscapeUtils.unescapeJson(variableValue.substring(1, variableValue.length() - 1))
                : variableValue;

        List<Object> results = List.of(new Variable().withWorkflowInstanceKey(workflowInstanceKey).withName(variableName)
                .withTimestamp(timestamp).withValue(value));

        logger.info("finding transformers for bpmn: {} and variable: {}", bpmn, variableName);
        List<TransferTransformerConfig.Transformer> matchingTransformers = transferTransformerConfig.getFlows().stream()
                .filter(it -> bpmn.equalsIgnoreCase(it.getName())).flatMap(it -> it.getTransformers().stream())
                .filter(it -> variableName.equalsIgnoreCase(it.getVariableName())).toList();

        logger.info("Processing variable {} and matchingTransformer size {}", variableName, matchingTransformers.size());

        matchTransformerForFlowType(flowType, bpmn, matchingTransformers, variableName, value, workflowInstanceKey);

        return results;
    }

    @Transactional
    private void matchTransformerForFlowType(String flowType, String bpmn, List<TransferTransformerConfig.Transformer> matchingTransformers,
            String variableName, String value, String workflowInstanceKey) {
        Optional<TransferTransformerConfig.Flow> config = transferTransformerConfig.findFlow(bpmn);
        if ("TRANSFER".equalsIgnoreCase(flowType)) {
            Transfer transfer = inFlightTransferManager.retrieveOrCreateTransfer(bpmn, workflowInstanceKey);
            matchingTransformers.forEach(transformer -> applyTransformer(transfer, variableName, value, transformer));
            logger.info(transfer.toString());
            transferRepository.save(transfer);
        } else {
            logger.error("No matching flow types for the given request");
        }
    }

    public List<Object> processTask(DocumentContext recordDocument, String workflowInstanceKey, String valueType, Long timestamp) {
        logger.info("Processing task instance");
        return List.of(new Task().withWorkflowInstanceKey(workflowInstanceKey).withTimestamp(timestamp)
                .withIntent(recordDocument.read("$.status", String.class)).withRecordType(valueType)
                .withType(recordDocument.read("$.taskType", String.class)).withElementId(recordDocument.read("$.taskId", String.class)));
    }

    private void applyTransformer(Object object, String variableName, String variableValue,
            TransferTransformerConfig.Transformer transformer) {
        logger.info("applying transformer for field: {}", transformer.getField());
        try {
            String fieldName = transformer.getField();
            if (Strings.isNotBlank(transformer.getConstant())) {
                logger.info("setting constant value: {}", transformer.getConstant());
                PropertyAccessorFactory.forBeanPropertyAccess(object).setPropertyValue(fieldName, transformer.getConstant());
                return;
            }

            if (Strings.isNotBlank(transformer.getJsonPath())) {
                logger.info("applying jsonpath for variable {}", variableName);
                DocumentContext json = JsonPathReader.parse(variableValue);
                Object result = json.read(transformer.getJsonPath());
                logger.info("jsonpath result: {} for variable {}", result, variableName);

                String value = null;
                if (result != null) {
                    if (result instanceof String) {
                        value = (String) result;
                    }
                    if (result instanceof List) {
                        value = ((List<?>) result).stream().map(Object::toString).collect(Collectors.joining(" "));
                    } else {
                        value = result.toString();
                    }
                    logger.info("FieldName - {} , value - {}", fieldName, value);
                    PropertyAccessorFactory.forBeanPropertyAccess(object).setPropertyValue(fieldName, value);
                }

                if (StringUtils.isBlank(value)) {
                    logger.error("null result when setting field {} from variable {}. Jsonpath: {}, variable value: {}", fieldName,
                            variableName, transformer.getJsonPath(), variableValue);
                }
                return;
            }

            if (Strings.isNotBlank(transformer.getXpath())) {
                logger.info("applying xpath for variable {}", variableName);
                Document document = documentBuilderFactory.newDocumentBuilder().parse(new InputSource(new StringReader(variableValue)));
                String result = pathFactory.newXPath().compile(transformer.getXpath()).evaluate(document);
                logger.info("xpath result: {} for variable {}", result, variableName);
                if (StringUtils.isNotBlank(result)) {
                    PropertyAccessorFactory.forBeanPropertyAccess(object).setPropertyValue(fieldName, result);
                } else {
                    logger.error("null result when setting field {} from variable {}. Xpath: {}, variable value: {}", fieldName,
                            variableName, transformer.getXpath(), variableValue);
                }
                return;
            }

            logger.info("setting simple variable value: {} for variable {}", variableValue, variableName);
            PropertyAccessorFactory.forBeanPropertyAccess(object).setPropertyValue(fieldName, variableValue);

        } catch (Exception e) {
            logger.error("failed to apply transformer {} to variable {}", transformer, variableName, e);
        }
    }

}
