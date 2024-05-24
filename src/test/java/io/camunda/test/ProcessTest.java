package io.camunda.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.response.*;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@ExtendWith(CamundaTestListener.class)
public class ProcessTest {

  private ZeebeClient zeebeClient;

  private CamundaTestContext camundaTestContext;

  @Test
  void shouldStartContainers() {
    assertThat(zeebeClient).isNotNull();
  }

  @Test
  void shouldRequestTopology() {
    // given
    // when
    var response = zeebeClient.newTopologyRequest().send().join();

    // then
    assertThat(response.getGatewayVersion()).isEqualTo("8.6.0-SNAPSHOT");
    assertThat(response.getBrokers())
        .flatExtracting(BrokerInfo::getPartitions)
        .extracting(PartitionInfo::getPartitionId, PartitionInfo::getHealth)
        .contains(tuple(1, PartitionBrokerHealth.HEALTHY));
  }

  @Test
  void shouldDeployProcess() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

    // when
    final DeploymentEvent deploymentEvent =
        zeebeClient
            .newDeployResourceCommand()
            .addProcessModel(process, "process.bpmn")
            .send()
            .join();

    // then
    final ProcessInstanceResult processInstanceResult =
        zeebeClient
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .withResult()
            .send()
            .join();

    assertThat(deploymentEvent.getProcesses()).hasSize(1);

    assertThat(processInstanceResult.getProcessInstanceKey()).isPositive();
  }

  @Test
  void shouldFindProcessInstance() throws URISyntaxException, IOException, InterruptedException {
    // given
    zeebeClient
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

    long processInstanceKey =
        zeebeClient
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();

    // when
    String operateRestEndpoint =
        "http://"
            + camundaTestContext.getOperateContainer().getHost()
            + ":"
            + camundaTestContext.getOperateContainer().getMappedPort(8080);

    // curl -c cookie.txt -X POST 'http://localhost:8080/api/login?username=demo&password=demo'
    HttpRequest authRequest =
        HttpRequest.newBuilder()
            .uri(new URI(operateRestEndpoint + "/api/login?username=demo&password=demo"))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();

    HttpClient httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .cookieHandler(new CookieManager())
            .build();

    httpClient.send(authRequest, HttpResponse.BodyHandlers.ofString());

    var before = Instant.now();

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI(operateRestEndpoint + "/v1/process-instances/" + processInstanceKey))
            .header("Content-Type", "application/json")
            .GET()
            .build();

    Awaitility.await()
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              HttpResponse<String> response =
                  httpClient.send(request, HttpResponse.BodyHandlers.ofString());

              // then
              assertThat(response.statusCode()).isEqualTo(200);
              assertThat(response.body()).contains("\"state\":\"ACTIVE\"");
            });

    var after = Instant.now();

    System.out.println("Time for assertion: " + Duration.between(before, after));
  }

  @Test
  void shouldCompleteUserTask() throws URISyntaxException, IOException, InterruptedException {
    // given
    zeebeClient
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
        zeebeClient
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .withResult()
            .send();

    long userTaskKey = 2251799813685256L;

    // when
    String zeebeRestEndpoint =
        "http://"
            + camundaTestContext.getZeebeContainer().getHost()
            + ":"
            + camundaTestContext.getZeebeContainer().getMappedPort(8080);

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

  @Test
  void shouldFindAndCompleteUserTask()
      throws URISyntaxException, IOException, InterruptedException {
    // given
    zeebeClient
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

    final long processInstanceKey =
        zeebeClient
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();

    // when
    String tasklistEndpoint =
        "http://"
            + camundaTestContext.getTasklistContainer().getHost()
            + ":"
            + camundaTestContext.getTasklistContainer().getMappedPort(8080);

    HttpRequest authRequest =
            HttpRequest.newBuilder()
                    .uri(new URI(tasklistEndpoint + "/api/login?username=demo&password=demo"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

    HttpClient httpClient =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .cookieHandler(new CookieManager())
                    .build();

    HttpResponse<String> authResponse = httpClient.send(authRequest, HttpResponse.BodyHandlers.ofString());
    assertThat(authResponse.statusCode()).isEqualTo(204);

    HttpRequest findTasksRequest =
        HttpRequest.newBuilder()
            .uri(new URI(tasklistEndpoint + "/v1/tasks/search"))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\n  \"processInstanceKey\": \"" + processInstanceKey + "\"}"))
            .build();

    final ObjectMapper objectMapper = new ObjectMapper();
    final var userTaskKey = new AtomicLong();

    Awaitility.await()
        .untilAsserted(
            () -> {
              HttpResponse<String> findTasksResponse =
                  httpClient.send(findTasksRequest, HttpResponse.BodyHandlers.ofString());

              assertThat(findTasksResponse.statusCode()).isEqualTo(200);

              List<TaskDto> searchTasksResponse =
                  objectMapper.readValue(findTasksResponse.body(), new TypeReference<List<TaskDto>>() {});

              assertThat(searchTasksResponse).isNotEmpty();

              TaskDto task = searchTasksResponse.getFirst();
              userTaskKey.set(Long.parseLong(task.id()));
            });

    String zeebeRestEndpoint =
        "http://"
            + camundaTestContext.getZeebeContainer().getHost()
            + ":"
            + camundaTestContext.getZeebeContainer().getMappedPort(8080);

    HttpRequest completeTaskRequest =
        HttpRequest.newBuilder()
            .uri(new URI(zeebeRestEndpoint + "/v1/user-tasks/" + userTaskKey.get() + "/completion"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\n  \"variables\": {\"x\":1}}"))
            .build();

    HttpResponse<String> completeTaskResponse =
        HttpClient.newBuilder()
            .build()
            .send(completeTaskRequest, HttpResponse.BodyHandlers.ofString());

    // then
    assertThat(completeTaskResponse.statusCode()).isEqualTo(204);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record TaskDto(String id) {}
}
