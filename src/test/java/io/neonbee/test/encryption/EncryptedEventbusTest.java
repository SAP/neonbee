package io.neonbee.test.encryption;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.helper.DummyVerticleHelper.createDummyDataVerticle;
import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;

import java.lang.reflect.Method;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeExtension.EncryptedEventbusTestBase;
import io.neonbee.NeonBeeInstanceConfiguration;
import io.neonbee.NeonBeeOptions;
import io.neonbee.data.DataException;
import io.neonbee.data.DataRequest;
import io.neonbee.data.DataVerticle;
import io.neonbee.data.internal.DataContextImpl;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.junit5.VertxTestContext;

class EncryptedEventbusTest extends EncryptedEventbusTestBase {
    private static final String DUMMY_PROVIDER_FQN = "provider";

    private static final String RESPONSE = "encrypted";

    private static final Path INCOMPATIBLE_KEYSTORE_PATH = TEST_RESOURCES.resolveRelated("incompatible-keystore.p12");

    private static final Path EXPIRED_KEYSTORE_PATH = TEST_RESOURCES.resolveRelated("expired-keystore.p12");

    @SuppressWarnings("PMD.CollapsibleIfStatements")
    @Override
    protected void configureTrust(NeonBeeOptions.Mutable options, ParameterContext parameterContext,
            ExtensionContext extensionContext) {
        super.configureTrust(options, parameterContext, extensionContext);

        String testMethodName = extensionContext.getTestMethod().map(Method::getName).orElse(null);
        if ("testEncryptionWithIncompatibleCertificate".equals(testMethodName)) {
            if (parameterContext.getIndex() == 1) {
                options.setClusterKeystore(INCOMPATIBLE_KEYSTORE_PATH);
            }
        } else if ("testEncryptionWithExpiredCertificate".equals(testMethodName)) {
            if (parameterContext.getIndex() == 1) {
                options.setClusterKeystore(EXPIRED_KEYSTORE_PATH);
            }
        }
    }

    @Test
    @DisplayName("Test that eventbus encryption works with valid certificates")
    void testEncryption(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}, encrypted = true) NeonBee provider,
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}, encrypted = true) NeonBee consumer,
            VertxTestContext testContext) {
        deployProviderVerticle(provider)
                .compose(v -> sendRequest(consumer, 0L))
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> assertThat(response).isEqualTo(RESPONSE));
                    testContext.completeNow();
                }));
    }

    @Test
    @DisplayName("Test that eventbus encryption won't work if only one node requires encryption")
    void testEncryptionOnlyOneNodeIsEncrypted(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}, encrypted = true) NeonBee provider,
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}, encrypted = false) NeonBee consumer,
            VertxTestContext testContext) {
        deployProviderVerticle(provider)
                // Unfortunately there is no better way to check if the eventbus is encrypted or not, than checking
                // if the message is delivered or not. Therefore, we limit timeout to 2 seconds.
                .compose(v -> sendRequest(consumer, 5_000L))
                .onComplete(expectCommunicationFailed(testContext));
    }

    @Test
    @DisplayName("Test that eventbus encryption won't work with incompatible certificates")
    void testEncryptionWithIncompatibleCertificate(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}, encrypted = true) NeonBee provider,
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}, encrypted = true) NeonBee consumer,
            VertxTestContext testContext) {
        deployProviderVerticle(provider)
                // Unfortunately there is no better way to check if the eventbus is encrypted or not, than checking
                // if the message is delivered or not. Therefore, we limit timeout to 2 seconds.
                .compose(v -> sendRequest(consumer, 5_000L))
                .onComplete(expectCommunicationFailed(testContext));
    }

    @Test
    @DisplayName("Test that eventbus encryption won't work with expired certificate")
    void testEncryptionWithExpiredCertificate(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}, encrypted = true) NeonBee provider,
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}, encrypted = true) NeonBee consumer,
            VertxTestContext testContext) {
        deployProviderVerticle(provider)
                // Unfortunately there is no better way to check if the eventbus is encrypted or not, than checking
                // if the message is delivered or not. Therefore, we limit timeout to 2 seconds.
                .compose(v -> sendRequest(consumer, 5_000L))
                .onComplete(expectCommunicationFailed(testContext));
    }

    private Future<Void> deployProviderVerticle(NeonBee provider) {
        DataVerticle<String> providerVerticle =
                createDummyDataVerticle(DUMMY_PROVIDER_FQN).withStaticResponse(RESPONSE);
        return provider.getVertx().deployVerticle(providerVerticle).mapEmpty();
    }

    private Future<String> sendRequest(NeonBee consumer, long timeout) {
        DataRequest request = new DataRequest(DUMMY_PROVIDER_FQN).setSendTimeout(timeout);
        return DataVerticle.requestData(consumer.getVertx(), request, new DataContextImpl());
    }

    private Handler<AsyncResult<String>> expectCommunicationFailed(VertxTestContext testContext) {
        return testContext.failing(t -> {
            testContext.verify(() -> {
                assertThat(t).isInstanceOf(DataException.class);
                assertThat(t).hasMessageThat().startsWith("Timed out");
                testContext.completeNow();
            });
        });
    }
}
