package io.camunda.test;

import io.camunda.zeebe.client.ZeebeClient;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.util.ExceptionUtils;
import org.junit.platform.commons.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.platform.commons.util.ReflectionUtils.makeAccessible;

public class CamundaTestListener implements BeforeEachCallback {

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
              CamundaTestContext camundaTestContext = new CamundaTestContext();
              camundaTestContext.start();
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
                final String zeebeGatewayAddress =
                    lookupOrCreate(context).getZeebeContainer().getExternalGatewayAddress();

                final String testTenantId = getTenantId(context);

                final ZeebeClient zeebeClient =
                    createZeebeClient(zeebeGatewayAddress, testTenantId);

                makeAccessible(field).set(testInstance, zeebeClient);

              } catch (final Throwable t) {
                ExceptionUtils.throwAsUncheckedException(t);
              }
            });
  }

  private static String getTenantId(ExtensionContext context) {
    final String testClassName = context.getTestClass().map(Class::getSimpleName).orElse("?");

    final String testMethodName = context.getTestMethod().map(Method::getName).orElse("?");

    return (testClassName + "_" + testMethodName).substring(0, 30);
  }

  private ZeebeClient createZeebeClient(final String gatewayAddress, final String tenantId) {
    return ZeebeClient.newClientBuilder()
        .gatewayAddress(gatewayAddress)
        .usePlaintext()
        .defaultTenantId(tenantId)
        .defaultJobWorkerTenantIds(List.of(tenantId))
        .build();
  }
}
