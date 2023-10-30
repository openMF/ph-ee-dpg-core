package org.mifos.connector.conductor;

import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConductorWorkflowStarter {

    @Autowired
    private WorkflowClient workflowClient;

    private String uri;

    public ConductorWorkflowStarter(@Value("${conductor.server.host}") String uri) {
        this.uri = uri;
    }

    public String startWorkflow(String workflowId, String apiRequest, Map<String, Object> extraVariables) {

        Map<String, Object> variables = new HashMap<>();
        variables.put(ConductorVariables.CHANNEL_REQUEST, apiRequest);
        variables.put(ConductorVariables.ORIGIN_DATE, Instant.now().toEpochMilli());

        if (extraVariables != null) {
            variables.putAll(extraVariables);
        }

        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName(workflowId);
        request.setVersion(1);
        request.setInput(variables);

        workflowClient.setRootURI(uri);
        return workflowClient.startWorkflow(request);
    }

}
