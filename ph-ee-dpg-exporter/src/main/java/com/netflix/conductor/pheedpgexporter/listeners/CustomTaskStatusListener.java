package com.netflix.conductor.pheedpgexporter.listeners;

import com.netflix.conductor.core.listener.TaskStatusListener;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.pheedpgexporter.kafkaexporter.PublishTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CustomTaskStatusListener implements TaskStatusListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomTaskStatusListener.class);

    @Autowired
    PublishTaskMessage publishTaskMessage;

    @Override
    public void onTaskScheduled(TaskModel task) {
        LOGGER.info("Task {} is scheduled", task.getTaskId());
        publishTaskMessage.sendMessage(task);
    }

    @Override
    public void onTaskCanceled(TaskModel task) {
        LOGGER.info("Task {} is canceled", task.getTaskId());
        publishTaskMessage.sendMessage(task);
    }

    @Override
    public void onTaskCompleted(TaskModel task) {
        LOGGER.info("Task {} is completed", task.getTaskId());
        publishTaskMessage.sendMessage(task);
    }

    @Override
    public void onTaskCompletedWithErrors(TaskModel task) {
        LOGGER.info("Task {} is completed with errors", task.getTaskId());
        publishTaskMessage.sendMessage(task);
    }

    @Override
    public void onTaskFailed(TaskModel task) {
        LOGGER.info("Task {} is failed", task.getTaskId());
        publishTaskMessage.sendMessage(task);
    }

    @Override
    public void onTaskFailedWithTerminalError(TaskModel task) {
        LOGGER.info("Task {} is failed with terminal error", task.getTaskId());
        publishTaskMessage.sendMessage(task);
    }

    @Override
    public void onTaskInProgress(TaskModel task) {
        LOGGER.info("Task {} is in-progress", task.getTaskId());
        publishTaskMessage.sendMessage(task);
    }

    @Override
    public void onTaskSkipped(TaskModel task) {
        LOGGER.info("Task {} is skipped", task.getTaskId());
        publishTaskMessage.sendMessage(task);
    }

    @Override
    public void onTaskTimedOut(TaskModel task) {
        LOGGER.info("Task {} is timed out", task.getTaskId());
        publishTaskMessage.sendMessage(task);
    }
}
