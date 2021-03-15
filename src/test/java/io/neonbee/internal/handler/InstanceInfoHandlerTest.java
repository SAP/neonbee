package io.neonbee.internal.handler;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.handler.InstanceInfoHandler.X_INSTANCE_INFO_HEADER;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.NeonBee;
import io.neonbee.test.base.DataVerticleTestBase;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.Timeout;

class InstanceInfoHandlerTest extends DataVerticleTestBase {
    @Test
    @DisplayName("Check the set X-Instance-Info header")
    @Timeout(value = 1, timeUnit = TimeUnit.SECONDS)
    void testXInstanceName(Vertx vertx) {
        createRequest(HttpMethod.GET, "/").send(asyncResponse -> {
            if (asyncResponse.succeeded()) {
                HttpResponse<Buffer> response = asyncResponse.result();
                // We expect that the configured instance name (NeonBeeOptions), is added to the response header by the
                // InstanceInfoHandler
                assertThat(response.getHeader(X_INSTANCE_INFO_HEADER))
                        .isEqualTo(NeonBee.instance(vertx).getOptions().getInstanceName());
            }
        });
    }
}
