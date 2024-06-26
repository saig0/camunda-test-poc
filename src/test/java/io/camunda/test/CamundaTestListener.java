package io.camunda.test;

import static org.junit.platform.commons.util.ReflectionUtils.makeAccessible;

import io.camunda.zeebe.client.ZeebeClient;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.util.ExceptionUtils;
import org.junit.platform.commons.util.ReflectionUtils;

public class CamundaTestListener implements BeforeEachCallback {

    private final boolean enableConnectors;
    private final Map<String, String> connectorSecrets;

    public CamundaTestListener() {
        this(false, Collections.emptyMap());
    }

    public CamundaTestListener(boolean enableConnectors, Map<String, String> connectorSecrets) {
        this.enableConnectors = enableConnectors;
        this.connectorSecrets = connectorSecrets;
    }

    @Override
  public void beforeEach(ExtensionContext extensionContext) throws Exception {
    extensionContext
        .getRequiredTestInstances()
        .getAllInstances()
        .forEach(instance -> injectFields(extensionContext, instance, instance.getClass()));
  }

  private ExtensionContext.Store getStore(final ExtensionContext context) {
    return context.getStore(ExtensionContext.Namespace.create(getClass(), context.getUniqueId()));
  }

  private CamundaTestContext lookupOrCreate(final ExtensionContext extensionContext) {
    final var store = getStore(extensionContext);

    return (CamundaTestContext)
        store.getOrComputeIfAbsent(
            "camunda-test-context",
            (key) -> {
              CamundaTestContext camundaTestContext = new CamundaTestContext(connectorSecrets);
              camundaTestContext.start(enableConnectors);
              return camundaTestContext;
            });
  }

  private void injectFields(
      final ExtensionContext context, final Object testInstance, final Class<?> testClass) {

    ReflectionUtils.findFields(
            testClass,
            field -> ReflectionUtils.isNotStatic(field) && field.getType() == ZeebeClient.class,
            ReflectionUtils.HierarchyTraversalMode.TOP_DOWN)
        .forEach(
            field -> {
              try {
                  final var camundaTestContext = lookupOrCreate(context);

                final String zeebeGatewayAddress =
                    camundaTestContext.getZeebeContainer().getExternalGatewayAddress();

                final ZeebeClient zeebeClient = createZeebeClient(zeebeGatewayAddress);

                makeAccessible(field).set(testInstance, zeebeClient);

              } catch (final Throwable t) {
                ExceptionUtils.throwAsUncheckedException(t);
              }
            });

      ReflectionUtils.findFields(
                      testClass,
                      field -> ReflectionUtils.isNotStatic(field) && field.getType() == CamundaTestContext.class,
                      ReflectionUtils.HierarchyTraversalMode.TOP_DOWN)
              .forEach(
                      field -> {
                          try {
                              final var camundaTestContext = lookupOrCreate(context);
                              makeAccessible(field).set(testInstance, camundaTestContext);

                          } catch (final Throwable t) {
                              ExceptionUtils.throwAsUncheckedException(t);
                          }
                      });
  }

  private ZeebeClient createZeebeClient(final String gatewayAddress) {
    return ZeebeClient.newClientBuilder().gatewayAddress(gatewayAddress).usePlaintext().build();
  }

  public static CamundaTestListener withConnectors(boolean enableConnectors, Map<String, String> connectorSecrets) {
        return new CamundaTestListener(enableConnectors, connectorSecrets);
  }
}
