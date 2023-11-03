package org.mifos.pheedpgimporterrdbms.streams;

import static org.apache.kafka.common.serialization.Serdes.ListSerde;

import com.jayway.jsonpath.DocumentContext;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Aggregator;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Merger;
import org.apache.kafka.streams.kstream.SessionWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.mifos.pheedpgimporterrdbms.config.TransferTransformerConfig;
import org.mifos.pheedpgimporterrdbms.entity.tenant.ThreadLocalContextUtil;
import org.mifos.pheedpgimporterrdbms.importer.JsonPathReader;
import org.mifos.pheedpgimporterrdbms.tenants.TenantsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class StreamsSetup {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private static final Serde<String> STRING_SERDE = Serdes.String();

    @Value("${importer.kafka.topic}")
    private String kafkaTopic;

    @Value("${importer.kafka.aggreation-window-seconds}")
    private int aggregationWindowSeconds;

    @Autowired
    private StreamsBuilder streamsBuilder;

    @Autowired
    TenantsService tenantsService;

    @Autowired
    TransferTransformerConfig transferTransformerConfig;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    RecordParser recordParser;

    @PostConstruct
    public void setup() {
        logger.info("## setting up kafka streams on topic `{}`, aggregating every {} seconds", kafkaTopic, aggregationWindowSeconds);
        Aggregator<String, String, List<String>> aggregator = (key, value, aggregate) -> {
            aggregate.add(value);
            return aggregate;
        };
        Merger<String, List<String>> merger = (key, first, second) -> Stream.of(first, second).flatMap(Collection::stream)
                .collect(Collectors.toList());

        streamsBuilder.stream(kafkaTopic, Consumed.with(STRING_SERDE, STRING_SERDE)).groupByKey()
                .windowedBy(SessionWindows.ofInactivityGapAndGrace(Duration.ofSeconds(aggregationWindowSeconds), Duration.ZERO))
                .aggregate(ArrayList::new, aggregator, merger, Materialized.with(STRING_SERDE, ListSerde(ArrayList.class, STRING_SERDE)))
                .toStream().foreach(this::process);

        // TODO kafka-ba kell leirni a vegen az entitaslistat, nem DB-be, hogy konzisztens es ujrajatszhato legyen !!
    }

    public void process(Object _key, Object _value) {

        Windowed<String> key = (Windowed<String>) _key;
        List<String> records = (List<String>) _value;

        if (records == null || records.size() == 0) {
            logger.warn("skipping processing, null records for key: {}", key);
            return;
        }

        String bpmn;
        String tenantName;
        String first = records.get(0);
        DocumentContext sample = JsonPathReader.parse(first);

        logger.info(key.toString());
        logger.info(records.toString());
        logger.info(_key.toString());
        logger.info(_value.toString());

        try {
            Pair<String, String> bpmnAndTenant = retrieveTenant(sample);
            bpmn = bpmnAndTenant.getFirst();
            tenantName = bpmnAndTenant.getSecond();
            logger.info("resolving tenant server connection for tenant: {}", tenantName);
            DataSource tenant = tenantsService.getTenantDataSource(tenantName);
            ThreadLocalContextUtil.setTenant(tenant);
        } catch (Exception e) {
            logger.error("failed to process first record: {}, skipping whole batch", first, e);
            return;
        }

        if (transferTransformerConfig.findFlow(bpmn).isEmpty()) {
            logger.warn("skip saving flow information, no configured flow found for bpmn: {}", bpmn);
            return;
        }

        Optional<TransferTransformerConfig.Flow> config = transferTransformerConfig.findFlow(bpmn);
        String flowType = getTypeForFlow(config);

        transactionTemplate.executeWithoutResult(status -> {
            for (String record : records) {
                try {
                    DocumentContext recordDocument = JsonPathReader.parse(record);
                    logger.info("from kafka: {}", recordDocument.jsonString());

                    String valueType;
                    // Long workflowKey = recordDocument.read("$.value.processDefinitionKey");
                    String workflowInstanceKey;
                    // Long timestamp = recordDocument.read("$.timestamp");
                    // String bpmnElementType = recordDocument.read("$.value.bpmnElementType");
                    // String elementId = recordDocument.read("$.value.elementId");

                    if (recordDocument.read("$.taskType") != null) {
                        valueType = "TASK";
                        workflowInstanceKey = recordDocument.read("$.workflowInstanceId");
                    } else {
                        valueType = "WORKFLOW";
                        workflowInstanceKey = recordDocument.read("$.workflowId");
                    }

                    logger.info("processing {} event", valueType);

                    logger.info("Processing document of type {}", valueType);

                    List<Object> entities = switch (valueType) {
                        case "TASK" -> {
                            yield recordParser.processTask(recordDocument, bpmn, flowType, workflowInstanceKey);
                        }
                        case "WORKFLOW" -> {
                            yield recordParser.processWorkflow(recordDocument, bpmn, flowType, workflowInstanceKey);
                        }
                        default -> throw new IllegalStateException("Unexpected event type: " + valueType);
                    };
                } catch (Exception e) {
                    logger.error("failed to parse record: {}", record, e);
                }
            }
        });
    }

    public Pair<String, String> retrieveTenant(DocumentContext record) {
        String bpmnProcessIdWithTenant = findBpmnProcessId(record);

        String[] split = bpmnProcessIdWithTenant.split("\\.");
        if (split.length < 2) {
            throw new RuntimeException("Invalid bpmnProcessId, has no tenant information: '" + bpmnProcessIdWithTenant + "'");
        }
        return Pair.of(split[0], split[1]);
    }

    private String findBpmnProcessId(DocumentContext record) {
        record.renameKey("$", "workflowName", "workflowType");
        String bpmnProcessIdWithTenant = record.read("$.workflowType", String.class);
        if (bpmnProcessIdWithTenant == null) {
            logger.warn("can't find bpmnProcessId in record: {}, trying alternative ways..", record.jsonString());
            List<String> ids = record.read("$..workflowType", List.class);
            if (ids.size() > 1) {
                throw new RuntimeException("Invalid bpmnProcessIdWithTenant, has more than one bpmnProcessIds: '" + ids + "'");
            }
            bpmnProcessIdWithTenant = ids.get(0);
        }
        logger.info("resolved bpmnProcessIdWithTenant: {}", bpmnProcessIdWithTenant);
        return bpmnProcessIdWithTenant;
    }

    private String getTypeForFlow(Optional<TransferTransformerConfig.Flow> config) {
        return config.map(TransferTransformerConfig.Flow::getType).orElse(null);
    }
}
