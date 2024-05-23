package io.camunda.test;

import static org.junit.platform.commons.util.ReflectionUtils.makeAccessible;

import io.camunda.zeebe.client.ZeebeClient;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.util.ExceptionUtils;
import org.junit.platform.commons.util.ReflectionUtils;

public class CamundaTestBeforeAllListener implements BeforeAllCallback {

  @Override
  public void beforeAll(ExtensionContext extensionContext) throws Exception {
    Class<?> testClass = extensionContext.getRequiredTestClass();
    injectFields(extensionContext, null, testClass);
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
              CamundaTestContext camundaTestContext = new CamundaTestContext();
              camundaTestContext.start(false);
              return camundaTestContext;
            });
  }

  private void injectFields(
      final ExtensionContext context, final Object testInstance, final Class<?> testClass) {
    ReflectionUtils.findFields(
            testClass,
            field -> field.getType() == ZeebeClient.class,
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
  }

  private ZeebeClient createZeebeClient(final String gatewayAddress) {
    return ZeebeClient.newClientBuilder().gatewayAddress(gatewayAddress).usePlaintext().build();
  }
}
