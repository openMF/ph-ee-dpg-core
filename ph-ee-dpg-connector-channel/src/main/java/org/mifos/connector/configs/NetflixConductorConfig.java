package org.mifos.connector.configs;

import com.netflix.conductor.client.automator.TaskRunnerConfigurer;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.client.worker.Worker;
import java.util.List;
import javax.annotation.PostConstruct;
import org.mifos.connector.conductor.netflixconductorworkers.TransferSampleWorker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NetflixConductorConfig {

    @Value("${conductor.server.host}")
    private String uri;

    @PostConstruct
    public void netflixConfig() {
        TaskClient taskClient = new TaskClient();
        taskClient.setRootURI(uri);

        int threadCount = 1;

        Worker worker3 = new TransferSampleWorker("initiate_input_params");
        TaskRunnerConfigurer configurer = new TaskRunnerConfigurer.Builder(taskClient, List.of(worker3)).withThreadCount(threadCount)
                .build();

        configurer.init();
    }

    @Bean
    WorkflowClient workflowClient() {
        return new WorkflowClient();
    }
}
