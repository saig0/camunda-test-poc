package io.camunda.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.*;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class ProcessConnectorTest {

  @RegisterExtension
  private final CamundaTestListener camundaTestListener =
      CamundaTestListener.withConnectors(
          true,
          Map.ofEntries(
              Map.entry("SLACK_TOKEN", "MY_SLACK_TOKEN"), // set connector secrets
              Map.entry(
                  "CONNECTOR_SLACK_OUTBOUND_TYPE",
                  "io.camunda:slack:1_disabled") // disable connector by overriding the job type
              ));

  private ZeebeClient zeebeClient;

  private CamundaTestContext camundaTestContext;

  @Test
  void shouldStartContainers() {
    assertThat(zeebeClient).isNotNull();
  }

  @Test
  void shouldRunConnector() {
    // given
    zeebeClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("weather-info.bpmn")
        .send()
        .join();

    // mock Slack worker
    zeebeClient
        .newStreamJobsCommand()
        .jobType("io.camunda:slack:1")
        .consumer(
            job -> {
              zeebeClient.newCompleteCommand(job).variable("slack", "is mocked").send().join();
            })
        .send();

    // when
    final ProcessInstanceResult processInstanceResult =
        zeebeClient
            .newCreateInstanceCommand()
            .bpmnProcessId("weather-info")
            .latestVersion()
            .withResult()
            .send()
            .join();

    // then
    assertThat(processInstanceResult.getVariablesAsMap())
        .containsKey("temperature")
        .containsEntry("slack", "is mocked");
  }
}
