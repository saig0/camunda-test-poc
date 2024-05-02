package io.camunda.test;

import io.zeebe.containers.ZeebeContainer;
import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

public class CamundaTestContext implements ExtensionContext.Store.CloseableResource {

  private static final Logger LOGGER = LoggerFactory.getLogger("io.camunda.test");

  private final Network network;
  private final ZeebeContainer zeebeContainer;
  private final ElasticsearchContainer elasticsearchContainer;
  private final GenericContainer<?> operateContainer;

  public CamundaTestContext() {

    network = Network.newNetwork();

    elasticsearchContainer = createElasticsearch(network);
    zeebeContainer = createZeebe(network);
    operateContainer = createOperate(network);
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

  public void start() {
    LOGGER.info("Starting containers...");

    final Stream<GenericContainer<?>> containers =
        Stream.of(elasticsearchContainer, zeebeContainer);

    containers.parallel().forEach(GenericContainer::start);

    operateContainer.start();

    LOGGER.info("...Container started");
  }

  @Override
  public void close() throws Throwable {
    LOGGER.info("Closing containers...");

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
}
