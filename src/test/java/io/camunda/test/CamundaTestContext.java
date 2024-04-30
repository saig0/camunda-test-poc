package io.camunda.test;

import io.zeebe.containers.ZeebeContainer;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.stream.Stream;

public class CamundaTestContext implements ExtensionContext.Store.CloseableResource {

  private static final Logger LOGGER = LoggerFactory.getLogger("io.camunda.test");

  private final Network network;
  private final ZeebeContainer zeebeContainer;
  private final ElasticsearchContainer elasticsearchContainer;

  private final PostgreSQLContainer<?> postgreSQLContainer;
  private final GenericContainer<?> keycloakContainer;
  private final GenericContainer<?> identityContainer;

  public CamundaTestContext() {

    network = Network.newNetwork();

    elasticsearchContainer = createElasticsearch(network);
    zeebeContainer = createZeebe(network);

    postgreSQLContainer = createPostgres(network);
    keycloakContainer = createKeycloak(network);
    identityContainer = createIdentity(network);
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
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE", "1")
        .withEnv("ZEEBE_BROKER_GATEWAY_MULTITENANCY_ENABLED", "true")
        .withEnv("ZEEBE_BROKER_GATEWAY_SECURITY_AUTHENTICATION_MODE", "identity")
        .withEnv(
            "ZEEBE_BROKER_GATEWAY_SECURITY_AUTHENTICATION_IDENTITY_ISSUERBACKENDURL",
            "http://keycloak:8080/auth/realms/camunda-platform")
        .withEnv("ZEEBE_BROKER_GATEWAY_SECURITY_AUTHENTICATION_IDENTITY_AUDIENCE", "zeebe-api")
        .withEnv(
            "ZEEBE_BROKER_GATEWAY_SECURITY_AUTHENTICATION_IDENTITY_BASEURL", "http://identity:8084")
        .withLogConsumer(new Slf4jLogConsumer(LOGGER));
  }

  private PostgreSQLContainer<?> createPostgres(final Network network) {
    return new PostgreSQLContainer<>(DockerImageName.parse("postgres:14.5-alpine"))
        .withNetwork(network)
        .withNetworkAliases("postgres")
        .withDatabaseName("bitnami_keycloak")
        .withUsername("bn_keycloak")
        .withPassword("#3]O?4RGj)DE7Z!9SA5")
        .withLogConsumer(new Slf4jLogConsumer(LOGGER));
  }

  private GenericContainer<?> createKeycloak(final Network network) {
    final var container =
        new GenericContainer<>(DockerImageName.parse("bitnami/keycloak:21.1.2"))
            .withNetwork(network)
            .withNetworkAliases("keycloak")
            .withEnv("KEYCLOAK_HTTP_RELATIVE_PATH", "/auth")
            .withEnv("KEYCLOAK_DATABASE_HOST", "postgres")
            .withEnv("KEYCLOAK_DATABASE_PASSWORD", "#3]O?4RGj)DE7Z!9SA5")
            .withEnv("KEYCLOAK_ADMIN_USER", "admin")
            .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
            .withLogConsumer(new Slf4jLogConsumer(LOGGER));
    container.addExposedPort(8080); // 18080
    return container;
  }

  private GenericContainer<?> createIdentity(final Network network) {
    final var container =
        new GenericContainer<>(DockerImageName.parse("camunda/identity:SNAPSHOT"))
            .withNetwork(network)
            .withNetworkAliases("identity")
            .withEnv("SERVER_PORT", "8084")
            .withEnv("IDENTITY_RETRY_DELAY_SECONDS", "30")
            .withEnv("KEYCLOAK_URL", "http://keycloak:8080/auth")
            .withEnv(
                "IDENTITY_AUTH_PROVIDER_BACKEND_URL",
                "http://keycloak:18080/auth/realms/camunda-platform")
            .withEnv("IDENTITY_DATABASE_HOST", "postgres")
            .withEnv("IDENTITY_DATABASE_PORT", "5432")
            .withEnv("IDENTITY_DATABASE_NAME", "bitnami_keycloak")
            .withEnv("IDENTITY_DATABASE_USERNAME", "bn_keycloak")
            .withEnv("IDENTITY_DATABASE_PASSWORD", "#3]O?4RGj)DE7Z!9SA5")
            .withEnv("KEYCLOAK_INIT_OPERATE_SECRET", "XALaRPl5qwTEItdwCMiPS62nVpKs7dL7")
            .withEnv("KEYCLOAK_INIT_OPERATE_ROOT_URL", "http://localhost:8081") // ${HOST} ?
            .withEnv("KEYCLOAK_INIT_TASKLIST_SECRET", "XALaRPl5qwTEItdwCMiPS62nVpKs7dL7")
            .withEnv("KEYCLOAK_INIT_TASKLIST_ROOT_URL", "http://localhost:8082")
            .withEnv("KEYCLOAK_INIT_OPTIMIZE_SECRET", "XALaRPl5qwTEItdwCMiPS62nVpKs7dL7")
            .withEnv("KEYCLOAK_INIT_OPTIMIZE_ROOT_URL", "http://localhost:8083")
            .withEnv("KEYCLOAK_INIT_WEBMODELER_ROOT_URL", "http://localhost:8070")
            .withEnv("KEYCLOAK_INIT_CONNECTORS_SECRET", "XALaRPl5qwTEItdwCMiPS62nVpKs7dL7")
            .withEnv("KEYCLOAK_INIT_CONNECTORS_ROOT_URL", "http://localhost:8085")
            .withEnv("KEYCLOAK_INIT_ZEEBE_NAME", "zeebe")
            .withEnv("KEYCLOAK_USERS_0_USERNAME", "demo")
            .withEnv("KEYCLOAK_USERS_0_PASSWORD", "demo")
            .withEnv("KEYCLOAK_USERS_0_FIRST_NAME", "demo")
            .withEnv("KEYCLOAK_USERS_0_EMAIL", "demo@acme.com")
            .withEnv("KEYCLOAK_USERS_0_ROLES_0", "Identity")
            .withEnv("KEYCLOAK_USERS_0_ROLES_1", "Optimize")
            .withEnv("KEYCLOAK_USERS_0_ROLES_2", "Operate")
            .withEnv("KEYCLOAK_USERS_0_ROLES_3", "Tasklist")
            .withEnv("KEYCLOAK_USERS_0_ROLES_4", "Web Modeler")
            .withEnv("KEYCLOAK_CLIENTS_0_NAME", "zeebe")
            .withEnv("KEYCLOAK_CLIENTS_0_ID", "zeebe") // ${ZEEBE_CLIENT_ID}
            .withEnv("KEYCLOAK_CLIENTS_0_SECRET", "zecret") // ${ZEEBE_CLIENT_SECRET}
            .withEnv("KEYCLOAK_CLIENTS_0_TYPE", "M2M")
            .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_0_RESOURCE_SERVER_ID", "zeebe-api")
            .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_0_DEFINITION", "write:*")
            .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_1_RESOURCE_SERVER_ID", "operate-api")
            .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_1_DEFINITION", "write:*")
            .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_2_RESOURCE_SERVER_ID", "tasklist-api")
            .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_2_DEFINITION", "write:*")
            .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_3_RESOURCE_SERVER_ID", "optimize-api")
            .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_3_DEFINITION", "write:*")
            .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_4_RESOURCE_SERVER_ID", "tasklist-api")
            .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_4_DEFINITION", "read:*")
            .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_5_RESOURCE_SERVER_ID", "operate-api")
            .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_5_DEFINITION", "read:*")
            .withEnv("MULTITENANCY_ENABLED", "true")
            .withEnv("RESOURCE_PERMISSIONS_ENABLED", "false")
            .withLogConsumer(new Slf4jLogConsumer(LOGGER));
    container.addExposedPort(8084);
    return container;
  }

  public void start() {
    LOGGER.info("Starting containers...");

    //    final Stream<GenericContainer<?>> containers =
    //        Stream.of(
    //            elasticsearchContainer,
    //            zeebeContainer,
    //            postgreSQLContainer,
    //            keycloakContainer,
    //            identityContainer);
    //
    //    containers.parallel().forEach(GenericContainer::start);

    postgreSQLContainer.start();
    keycloakContainer.start();
    identityContainer.start();

    elasticsearchContainer.start();
    zeebeContainer.start();

    LOGGER.info("...Container started");
  }

  @Override
  public void close() throws Throwable {
    LOGGER.info("Closing containers...");

    zeebeContainer.shutdownGracefully(Duration.ofSeconds(10));
    elasticsearchContainer.stop();

    identityContainer.stop();
    keycloakContainer.stop();
    postgreSQLContainer.stop();

    network.close();

    LOGGER.info("...Containers closed.");
  }

  public ZeebeContainer getZeebeContainer() {
    return zeebeContainer;
  }

  public ElasticsearchContainer getElasticsearchContainer() {
    return elasticsearchContainer;
  }
}
