package io.camunda.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.*;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(CamundaTestBeforeAllListener.class)
public class ProcessWithReusedEnvironmentTest {

  private static ZeebeClient ZEEBE_CLIENT;

  @Test
  void shouldStartContainers() {
    assertThat(ZEEBE_CLIENT).isNotNull();
  }

  @Test
  void shouldRequestTopology() {
    // given
    // when
    var response = ZEEBE_CLIENT.newTopologyRequest().send().join();

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
        ZEEBE_CLIENT
            .newDeployResourceCommand()
            .addProcessModel(process, "process.bpmn")
            .send()
            .join();

    // then
    final ProcessInstanceResult processInstanceResult =
        ZEEBE_CLIENT
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .withResult()
            .send()
            .join();

    assertThat(deploymentEvent.getProcesses()).hasSize(1);

    assertThat(processInstanceResult.getProcessInstanceKey()).isPositive();
  }
}
