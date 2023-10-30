package com.netflix.conductor.pheedpgexporter.listeners;

import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.pheedpgexporter.kafkaexporter.PublishWorkflowMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CustomWorkflowStatusListener implements WorkflowStatusListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomWorkflowStatusListener.class);

    @Autowired
    PublishWorkflowMessage publishWorkflowMessage;

    @Override
    public void onWorkflowCompleted(WorkflowModel workflow) {
        LOGGER.info("Workflow {} is completed", workflow.getWorkflowId());
        publishWorkflowMessage.sendMessage(workflow);
    }

    @Override
    public void onWorkflowTerminated(WorkflowModel workflow) {
        LOGGER.info("Workflow {} is terminated", workflow.getWorkflowId());
        publishWorkflowMessage.sendMessage(workflow);
    }

    @Override
    public void onWorkflowFinalized(WorkflowModel workflow) {
        LOGGER.info("Workflow {} is finalized", workflow.getWorkflowId());
        publishWorkflowMessage.sendMessage(workflow);
    }
}
