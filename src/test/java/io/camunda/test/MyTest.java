package io.camunda.test;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.containers.ZeebeContainer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class MyTest {

  private static final Logger LOGGER = LoggerFactory.getLogger("io.camunda.test");

  @Container
  private final ZeebeContainer zeebeContainer =
      new ZeebeContainer(
              // DockerImageName.parse("camunda/zeebe:8.5.0")
              DockerImageName.parse("camunda/zeebe:SNAPSHOT"))
          .withAdditionalExposedPort(8080)
          .withLogConsumer(new Slf4jLogConsumer(LOGGER));

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
    final DeploymentEvent deploymentEvent =
        client.newDeployCommand().addProcessModel(process, "process.bpmn").send().join();

    // then
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

  @Test
  void shouldCompleteUserTask() throws URISyntaxException, IOException, InterruptedException {
    // given
    ZeebeClient client = createClient();

    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .userTask("A")
                .zeebeUserTask()
                .endEvent()
                .done(),
            "process.bpmn")
        .send()
        .join();

    ZeebeFuture<ProcessInstanceResult> resultFuture =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .withResult()
            .send();

    long userTaskKey = 2251799813685256L;

    // when
    String zeebeRestEndpoint = "http://" + zeebeContainer.getExternalAddress(8080);

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI(zeebeRestEndpoint + "/v1/user-tasks/" + userTaskKey + "/completion"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\n  \"variables\": {\"x\":1}}"))
            .build();

    HttpResponse<String> response =
        HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());

    // then
    assertThat(response.statusCode()).isEqualTo(204);

    assertThat(resultFuture.join().getVariablesAsMap()).containsEntry("x", 1);
  }
}
