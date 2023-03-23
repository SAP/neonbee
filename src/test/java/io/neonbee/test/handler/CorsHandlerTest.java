package io.neonbee.test.handler;

import static com.google.common.truth.Truth.assertThat;
import static io.vertx.core.http.HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.vertx.core.http.HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.vertx.core.http.HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS;
import static io.vertx.core.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.vertx.core.http.HttpHeaders.ACCESS_CONTROL_MAX_AGE;
import static io.vertx.core.http.HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.vertx.core.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static io.vertx.core.http.HttpHeaders.ORIGIN;
import static io.vertx.core.http.HttpMethod.OPTIONS;
import static io.vertx.core.http.HttpMethod.POST;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.neonbee.config.CorsConfig;
import io.neonbee.config.ServerConfig;
import io.neonbee.internal.verticle.ServerVerticle;
import io.neonbee.test.base.NeonBeeTestBase;
import io.neonbee.test.helper.WorkingDirectoryBuilder;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.junit5.VertxTestContext;

class CorsHandlerTest extends NeonBeeTestBase {
    private static final CorsConfig DUMMY_CORS_CONFIG =
            new CorsConfig().setEnabled(true).setOrigins(List.of("http://foo.bar"))
                    .setRelativeOrigins(List.of("^http\\://.*.foo.bar$")).setAllowedHeaders(Set.of("foobar"))
                    .setAllowCredentials(true).setMaxAgeSeconds(1337).setAllowedMethods(Set.of("GET"))
                    .setExposedHeaders(Set.of("exposedHeader"));

    @Override
    protected WorkingDirectoryBuilder provideWorkingDirectoryBuilder(TestInfo testInfo, VertxTestContext testContext) {
        CorsConfig cc = new CorsConfig(DUMMY_CORS_CONFIG.toJson());
        return super.provideWorkingDirectoryBuilder(testInfo, testContext).setCustomTask(root -> {
            DeploymentOptions opts = WorkingDirectoryBuilder.readDeploymentOptions(ServerVerticle.class, root);
            ServerConfig sc = new ServerConfig(opts.getConfig());
            sc.setCorsConfig(cc);
            WorkingDirectoryBuilder.writeDeploymentOptions(ServerVerticle.class, opts.setConfig(sc.toJson()), root);
        });
    }

    @Test
    void testPreflightRequest(VertxTestContext testContext) {
        HttpRequest<Buffer> req = createRequest(OPTIONS, "/");
        req.putHeader(ORIGIN.toString(), "http://bla.foo.bar");
        req.putHeader(ACCESS_CONTROL_REQUEST_HEADERS.toString(), "someHeader");
        req.putHeader(ACCESS_CONTROL_REQUEST_METHOD.toString(), POST.toString());

        req.send().onSuccess(resp -> testContext.verify(() -> {
            assertThat(resp.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN.toString())).isEqualTo("http://bla.foo.bar");
            assertThat(resp.headers().get(ACCESS_CONTROL_ALLOW_CREDENTIALS.toString())).isEqualTo("true");
            assertThat(resp.headers().get(ACCESS_CONTROL_ALLOW_METHODS.toString())).isEqualTo("GET");
            assertThat(resp.headers().get(ACCESS_CONTROL_ALLOW_HEADERS.toString())).isEqualTo("foobar");
            assertThat(resp.headers().get(ACCESS_CONTROL_MAX_AGE.toString())).isEqualTo("1337");
            testContext.completeNow();
        })).onFailure(testContext::failNow);
    }
}
