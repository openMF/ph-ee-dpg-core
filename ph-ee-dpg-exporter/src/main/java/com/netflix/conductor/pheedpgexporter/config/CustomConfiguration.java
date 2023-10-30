package com.netflix.conductor.pheedpgexporter.config;

import com.netflix.conductor.core.listener.TaskStatusListener;
import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.pheedpgexporter.listeners.CustomTaskStatusListener;
import com.netflix.conductor.pheedpgexporter.listeners.CustomWorkflowStatusListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomConfiguration {

    @ConditionalOnProperty(name = "conductor.workflow-status-listener.type", havingValue = "custom")
    @Bean
    public WorkflowStatusListener workflowStatusListener() {
        return new CustomWorkflowStatusListener();
    }

    @ConditionalOnProperty(name = "conductor.task-status-listener.type", havingValue = "custom")
    @Bean
    public TaskStatusListener taskStatusListener() {
        return new CustomTaskStatusListener();
    }
}
