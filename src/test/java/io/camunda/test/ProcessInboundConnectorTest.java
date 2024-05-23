package io.camunda.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.*;

import java.net.CookieManager;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class ProcessInboundConnectorTest {

  @RegisterExtension
  private final CamundaTestListener camundaTestListener =
      CamundaTestListener.withConnectors(true, Collections.emptyMap());

  private ZeebeClient zeebeClient;

  private CamundaTestContext camundaTestContext;

  @Test
  void shouldStartContainers() {
    assertThat(zeebeClient).isNotNull();
  }

  @Test
  void shouldRunConnector() throws URISyntaxException {
    // given
    zeebeClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("inbound-demo.bpmn")
        .send()
        .join();

    // when
    String connectorsEndpoint =
        "http://"
            + camundaTestContext.getConnectorsContainer().getHost()
            + ":"
            + camundaTestContext.getConnectorsContainer().getMappedPort(8080);

    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI(connectorsEndpoint + "/inbound/9cb2fd06-70f7-4a22-ac50-a9dddc44e25a"))
            .header("Content-Type", "application/json")
            .GET()
            .build();

    Awaitility.await()
        .untilAsserted(
            () -> {
              HttpResponse<String> response =
                  httpClient.send(request, HttpResponse.BodyHandlers.ofString());

              // then
              assertThat(response.statusCode()).isEqualTo(200);
              assertThat(response.body()).contains("processInstanceKey");
            });
  }
}
