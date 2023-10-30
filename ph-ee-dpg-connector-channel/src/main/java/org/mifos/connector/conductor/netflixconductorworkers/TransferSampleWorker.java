package org.mifos.connector.conductor.netflixconductorworkers;

import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;

public class TransferSampleWorker implements Worker {

    private final String taskDefName;

    public TransferSampleWorker(String taskDefName) {
        this.taskDefName = taskDefName;
    }

    @Override
    public String getTaskDefName() {
        return taskDefName;
    }

    @Override
    public TaskResult execute(Task task) {

        TaskResult result = new TaskResult(task);
        result.addOutputData("sum", "out1");
        result.setStatus(TaskResult.Status.COMPLETED);
        return result;
    }
}
