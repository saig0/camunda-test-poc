package io.camunda.test;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.containers.ZeebeContainer;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class MyTest {

    @Container
    private final ZeebeContainer zeebeContainer = new ZeebeContainer(
            // DockerImageName.parse("camunda/zeebe:8.5.0")
            DockerImageName.parse("camunda/zeebe:SNAPSHOT")
    );

    private ZeebeClient createClient() {
        final ZeebeClient client =
                ZeebeClient.newClientBuilder()
                        .gatewayAddress(zeebeContainer.getExternalGatewayAddress())
                        .usePlaintext()
                        .build();
        return client;
    }

    @Test
    void testZeebeVersion() {
        // given
        final ZeebeClient client = createClient();

        // when
        String gatewayVersion = client.newTopologyRequest().send().join().getGatewayVersion();

        // then
        assertThat(gatewayVersion).isEqualTo("8.6.0-SNAPSHOT");
    }

    @Test
    void shouldConnectToZeebe() {
        // given
        final ZeebeClient client = createClient();
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
        assertThat(processInstanceResult.getProcessDefinitionKey())
                .isEqualTo(deploymentEvent.getProcesses().get(0).getProcessDefinitionKey());
    }


}
