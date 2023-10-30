package com.netflix.conductor.pheedpgexporter.kafkaexporter;

import com.netflix.conductor.model.TaskModel;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PublishTaskMessage {

    private final KafkaTemplate<String, TaskModel> kafkaTemplate;

    public PublishTaskMessage(KafkaTemplate<String, TaskModel> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendMessage(TaskModel model) {
        kafkaTemplate.send("conductor", model.getTaskId(), model);
    }
}
