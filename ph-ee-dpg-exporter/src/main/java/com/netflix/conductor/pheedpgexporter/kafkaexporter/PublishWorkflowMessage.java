package com.netflix.conductor.pheedpgexporter.kafkaexporter;

import com.netflix.conductor.model.WorkflowModel;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PublishWorkflowMessage {

    private final KafkaTemplate<String, WorkflowModel> kafkaTemplate;

    public PublishWorkflowMessage(KafkaTemplate<String, WorkflowModel> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendMessage(WorkflowModel model) {
        kafkaTemplate.send("conductor", model.getWorkflowId(), model);
    }
}
