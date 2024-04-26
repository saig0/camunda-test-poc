package io.camunda.test;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.containers.ZeebeContainer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class MyTest {

    @Container
    private final ZeebeContainer zeebeContainer = new ZeebeContainer();

    @Test
    void shouldConnectToZeebe() {
        // given
        final ZeebeClient client =
                ZeebeClient.newClientBuilder()
                        .gatewayAddress(zeebeContainer.getExternalGatewayAddress())
                        .usePlaintext()
                        .build();
        final BpmnModelInstance process =
                Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

        // when
        // do something (e.g. deploy a process)
        final DeploymentEvent deploymentEvent =
                client.newDeployCommand().addProcessModel(process, "process.bpmn").send().join();

        // then
        // verify (e.g. we can create an instance of the deployed process)
        final ProcessInstanceResult processInstanceResult =
                client
                        .newCreateInstanceCommand()
                        .bpmnProcessId("process")
                        .latestVersion()
                        .withResult()
                        .send()
                        .join();
        Assertions.assertThat(processInstanceResult.getProcessDefinitionKey())
                .isEqualTo(deploymentEvent.getProcesses().get(0).getProcessDefinitionKey());
    }

}
