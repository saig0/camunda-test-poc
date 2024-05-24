package io.camunda.test;

import io.zeebe.containers.ZeebeContainer;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

public class CamundaTestContext implements ExtensionContext.Store.CloseableResource {

  private static final Logger LOGGER = LoggerFactory.getLogger("io.camunda.test");

  private final Network network;
  private final ZeebeContainer zeebeContainer;
  private final ElasticsearchContainer elasticsearchContainer;
  private final GenericContainer<?> operateContainer;
  private final GenericContainer<?> tasklistContainer;
  private final GenericContainer<?> connectorsContainer;

  public CamundaTestContext() {
    this(Collections.emptyMap());
  }

  public CamundaTestContext(Map<String, String> connectorSecrets) {
    network = Network.newNetwork();

    elasticsearchContainer = createElasticsearch(network);
    zeebeContainer = createZeebe(network);
    operateContainer = createOperate(network);
    tasklistContainer = createTasklist(network);
    connectorsContainer = createConnectors(network, connectorSecrets);
  }

  private ElasticsearchContainer createElasticsearch(final Network network) {
    return new ElasticsearchContainer(DockerImageName.parse("elasticsearch:8.13.0"))
        .withEnv("xpack.security.enabled", "false")
        .withNetwork(network)
        .withNetworkAliases("elasticsearch");
  }

  private ZeebeContainer createZeebe(final Network network) {
    return new ZeebeContainer(DockerImageName.parse("camunda/zeebe:SNAPSHOT"))
        .withNetwork(network)
        .withNetworkAliases("zeebe")
        .withAdditionalExposedPort(8080)
        .withEnv(
            "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME",
            "io.camunda.zeebe.exporter.ElasticsearchExporter")
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL", "elasticsearch:9200")
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE", "1");
  }

  private GenericContainer<?> createOperate(final Network network) {
    final var container =
        new GenericContainer<>(DockerImageName.parse("camunda/operate:SNAPSHOT"))
            .withNetwork(network)
            .withNetworkAliases("operate")
            .withLogConsumer(new Slf4jLogConsumer(LOGGER))
            .withEnv("CAMUNDA_OPERATE_ZEEBE_GATEWAYADDRESS", "zeebe:26500")
            .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_URL", "http://elasticsearch:9200")
            .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_URL", "http://elasticsearch:9200");
    container.addExposedPort(8080);
    return container;
  }

  private GenericContainer<?> createTasklist(final Network network) {
    final var container =
        new GenericContainer<>(DockerImageName.parse("camunda/tasklist:SNAPSHOT"))
            .withNetwork(network)
            .withNetworkAliases("tasklist")
            .withLogConsumer(new Slf4jLogConsumer(LOGGER))
            .withEnv("CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS", "zeebe:26500")
            .withEnv("CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS", "http://zeebe:8080")
            .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", "http://elasticsearch:9200")
            .withEnv("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_URL", "http://elasticsearch:9200")
            .withEnv("CAMUNDA_TASKLIST_CSRFPREVENTIONENABLED", "false"); // disable CSRF protection
    container.addExposedPort(8080);
    return container;
  }

  private GenericContainer<?> createConnectors(
      final Network network, Map<String, String> connectorSecrets) {
    final var container =
        new GenericContainer<>(DockerImageName.parse("camunda/connectors-bundle:SNAPSHOT"))
            .withNetwork(network)
            .withNetworkAliases("connectors")
            .withLogConsumer(new Slf4jLogConsumer(LOGGER))
            .withEnv("ZEEBE_CLIENT_BROKER_GATEWAY-ADDRESS", "zeebe:26500")
            .withEnv("ZEEBE_CLIENT_SECURITY_PLAINTEXT", "true")
            .withEnv("CAMUNDA_OPERATE_CLIENT_URL", "http://operate:8080")
            .withEnv("CAMUNDA_OPERATE_CLIENT_USERNAME", "demo")
            .withEnv("CAMUNDA_OPERATE_CLIENT_PASSWORD", "demo");

    connectorSecrets.forEach(container::addEnv);

    container.addExposedPort(8080);
    return container;
  }

  public void start(final boolean enabledConnectors) {
    LOGGER.info("Starting containers...");

    final Stream<GenericContainer<?>> containers =
        Stream.of(elasticsearchContainer, zeebeContainer);

    containers.parallel().forEach(GenericContainer::start);

    operateContainer.start();
    tasklistContainer.start();

    if (enabledConnectors) {
      connectorsContainer.start();
    }

    LOGGER.info("...Container started");
  }

  @Override
  public void close() throws Throwable {
    LOGGER.info("Closing containers...");

    if (connectorsContainer.isRunning()) {
      connectorsContainer.stop();
    }

    tasklistContainer.stop();
    operateContainer.stop();
    zeebeContainer.shutdownGracefully(Duration.ofSeconds(10));
    elasticsearchContainer.stop();

    network.close();

    LOGGER.info("...Containers closed.");
  }

  public ZeebeContainer getZeebeContainer() {
    return zeebeContainer;
  }

  public ElasticsearchContainer getElasticsearchContainer() {
    return elasticsearchContainer;
  }

  public GenericContainer<?> getOperateContainer() {
    return operateContainer;
  }

  public GenericContainer<?> getConnectorsContainer() {
    return connectorsContainer;
  }

  public GenericContainer<?> getTasklistContainer() {
    return tasklistContainer;
  }
}
