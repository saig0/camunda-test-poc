package io.camunda.test;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.containers.ZeebeContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class ElasticsearchTest {

  private static final Logger LOGGER = LoggerFactory.getLogger("io.camunda.test");

  private String zeebeGatewayAddress;
  private String elasticSearchAddress;

  private ZeebeClient createClient(final String zeebeGatewayAddress) {
    return ZeebeClient.newClientBuilder()
        .gatewayAddress(zeebeGatewayAddress)
        .usePlaintext()
        .build();
  }

  @BeforeEach
  public void init() {
    final var network = Network.newNetwork();

    Stream<Consumer<Network>> containers = Stream.of(
            this::startElasticsearch,
            this::startZeebe
    );

    containers.parallel().forEach(container -> container.accept(network));
  }

  private void startZeebe(final Network network) {
    final ZeebeContainer zeebeContainer =
        new ZeebeContainer(
                // DockerImageName.parse("camunda/zeebe:8.5.0")
                DockerImageName.parse("camunda/zeebe:SNAPSHOT"))
            .withNetwork(network)
            .withNetworkAliases("zeebe")
            .withAdditionalExposedPort(8080)
            .withEnv(
                "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME",
                "io.camunda.zeebe.exporter.ElasticsearchExporter")
            .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL", "elasticsearch:9200")
            .withLogConsumer(new Slf4jLogConsumer(LOGGER));

    zeebeContainer.start();

    zeebeGatewayAddress = zeebeContainer.getExternalGatewayAddress();
  }

  private void startElasticsearch(final Network network) {

    var elasticsearchContainer =
        new ElasticsearchContainer(DockerImageName.parse("elasticsearch:8.13.0"))
            // DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.9.2"))
            .withEnv("xpack.security.enabled", "false")
            .withNetwork(network)
            .withNetworkAliases("elasticsearch");

    elasticsearchContainer.start();

    elasticSearchAddress = elasticsearchContainer.getHttpHostAddress();

    LOGGER.info("ES address > {}", elasticSearchAddress);
  }

  @Test
  void shouldExportToElasticsearch() {
    // given
    final ZeebeClient client = createClient(zeebeGatewayAddress);
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

    // when
    client.newDeployCommand().addProcessModel(process, "process.bpmn").send().join();

    // then
    var elasticsearchRequest =
        HttpRequest.newBuilder()
            .uri(URI.create("http://" + elasticSearchAddress + "/_stats"))
            .GET()
            .build();

    Awaitility.await()
        .untilAsserted(
            () -> {
              assertThat(
                      HttpClient.newHttpClient()
                          .send(elasticsearchRequest, HttpResponse.BodyHandlers.ofString())
                          .body())
                  .contains("zeebe-record_deployment");
            });
  }
}
