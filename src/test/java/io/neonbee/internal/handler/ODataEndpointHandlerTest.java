package io.neonbee.internal.handler;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.Helper.EMPTY;
import static io.neonbee.internal.handler.ODataEndpointHandler.UriConversion.CDS;
import static io.neonbee.internal.handler.ODataEndpointHandler.UriConversion.LOOSE;
import static io.neonbee.internal.handler.ODataEndpointHandler.UriConversion.STRICT;
import static io.neonbee.internal.handler.ODataEndpointHandler.mapODataResponse;
import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;
import static io.vertx.core.CompositeFuture.all;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.server.api.ODataContent;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ODataResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

import com.google.common.base.Charsets;

import io.neonbee.internal.handler.ODataEndpointHandler.UriConversion;
import io.neonbee.internal.verticle.ServerVerticle;
import io.neonbee.test.base.ODataEndpointTestBase;
import io.neonbee.test.helper.WorkingDirectoryBuilder;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

public class ODataEndpointHandlerTest extends ODataEndpointTestBase {

    @Override
    protected List<Path> provideEntityModels() {
        return List.of(TEST_RESOURCES.resolveRelated("TestService1.csn"),
                TEST_RESOURCES.resolveRelated("TestService2.csn"), TEST_RESOURCES.resolveRelated("TestService3.csn"));
    }

    @Override
    protected WorkingDirectoryBuilder provideWorkingDirectoryBuilder(TestInfo testInfo, VertxTestContext testContext) {
        return super.provideWorkingDirectoryBuilder(testInfo, testContext).setCustomTask(root -> {
            // the server verticle should either use strict, cds or loose URI mapping
            String testMethodName = testInfo.getTestMethod().map(Method::getName).orElse(EMPTY);
            UriConversion uriConversion = STRICT;
            if (testMethodName.contains("LooseUriConversion")) {
                uriConversion = LOOSE;
            } else if (testMethodName.contains("CDSUriConversion")) {
                uriConversion = CDS;
            }

            DeploymentOptions opts = WorkingDirectoryBuilder.readDeploymentOptions(ServerVerticle.class, root);
            opts.getConfig().put("endpoints",
                    new JsonObject().put("odata", new JsonObject().put("uriConversion", uriConversion.toString())));
            WorkingDirectoryBuilder.writeDeploymentOptions(ServerVerticle.class, opts, root);
        });
    }

    @Test
    @DisplayName("map generic response")
    public void checkGenericResponseMapping() {
        ODataResponse odataResponse = new ODataResponse();
        odataResponse.setStatusCode(200);
        odataResponse.setHeader("expected1", "value1");
        odataResponse.setHeader("expected2", "value2");
        odataResponse.setContent(new ByteArrayInputStream("expected data".getBytes(Charsets.UTF_8)));

        HttpServerResponse responseMock = mock(HttpServerResponse.class);
        assertDoesNotThrow(() -> mapODataResponse(odataResponse, responseMock));

        verify(responseMock).setStatusCode(200);
        verify(responseMock).putHeader("expected1", "value1");
        verify(responseMock).putHeader("expected2", "value2");

        ArgumentCaptor<Buffer> endBuffer = ArgumentCaptor.forClass(Buffer.class);
        verify(responseMock).end(endBuffer.capture());
        assertThat(endBuffer.getValue().toString()).isEqualTo("expected data");
    }

    @Test
    @DisplayName("map OData response")
    public void checkODataResponseMapping() {
        ODataResponse odataResponse = new ODataResponse();
        ODataContent odataContentMock = mock(ODataContent.class);
        doAnswer((Answer<ODataContent>) invocation -> {
            invocation.<OutputStream>getArgument(0).write("expected data".getBytes(Charsets.UTF_8));
            return null;
        }).when(odataContentMock).write(any(OutputStream.class));
        odataResponse.setODataContent(odataContentMock);

        HttpServerResponse responseMock = mock(HttpServerResponse.class);
        assertDoesNotThrow(() -> mapODataResponse(odataResponse, responseMock));

        ArgumentCaptor<Buffer> endBuffer = ArgumentCaptor.forClass(Buffer.class);
        verify(responseMock).end(endBuffer.capture());
        assertThat(endBuffer.getValue().toString()).isEqualTo("expected data");
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("check if (lazy) loading OData models on first request to ODataEndpoint works")
    public void testODataEndpointLazyLoading(VertxTestContext testContext) {
        assertOData(requestMetadata("io.neonbee.handler.TestService"),
                body -> assertThat(body.toString()).contains("<edmx:Edmx"), testContext)
                        .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @DisplayName("test mapToODataRequest")
    public void testMapToODataRequest() throws Exception {
        String expectedQuery = "$filter=foo eq 'bar'&$limit=42";
        String expectedPath = "/path";
        String expectedNamespace = "namespace";

        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.path()).thenReturn(expectedPath);
        when(request.query()).thenReturn(expectedQuery);
        when(request.scheme()).thenReturn("https");
        when(request.method()).thenReturn(HttpMethod.GET);
        when(request.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());

        RoutingContext routingContext = mock(RoutingContext.class);
        when(routingContext.request()).thenReturn(request);
        when(routingContext.getBody()).thenReturn(Buffer.buffer(""));
        when(routingContext.currentRoute()).thenReturn(null);
        when(routingContext.get(CorrelationIdHandler.CORRELATION_ID)).thenReturn("correlId");

        ODataRequest odataReq = ODataEndpointHandler.mapToODataRequest(routingContext, expectedNamespace);
        assertThat(odataReq.getRawRequestUri()).isEqualTo("/" + expectedNamespace + expectedPath + "?" + expectedQuery);
        assertThat(odataReq.getRawODataPath()).isEqualTo(expectedPath);
        assertThat(odataReq.getRawQueryPath()).isEqualTo(expectedQuery);
    }

    @Test
    public void testUriConversionRules() {
        // strict should not convert anything!
        assertThat(STRICT.apply("any-string")).isEqualTo("any-string");
        assertThat(STRICT.apply("anyOtherString")).isEqualTo("anyOtherString");
        assertThat(STRICT.apply("any_non UsUalST1NG")).isEqualTo("any_non UsUalST1NG");
        assertThat(STRICT.apply(EMPTY)).isEqualTo(EMPTY);

        // cds has some very specific rules tested with any of the below statements
        assertThat(CDS.apply("any.namespace.HAS.2.be.ReMo_VeD.Test")).isEqualTo("test");
        assertThat(CDS.apply("remove_any_tailing_'Service'_string.TestService")).isEqualTo("test");
        assertThat(CDS.apply("lowerCaseForTheFirstLetterOfTheService.FooBarService")).isEqualTo("foo-bar");
        assertThat(CDS.apply("concatenateWordsWith-.FOOBarBAZService")).isEqualTo("foobarbaz");
        assertThat(CDS.apply("everythingGoesLowerCase.FOO-BAR-BAZ")).isEqualTo("foo-bar-baz");
        assertThat(CDS.apply("replaceAny_With-.FOO_BAR")).isEqualTo("foo-bar");

        // special case, naming a service in a namespace only "Service" will result in the empty name for the service
        assertThat(CDS.apply("any.namespace.Service")).isEqualTo("");

        // some more real examples
        assertThat(LOOSE.apply("my.very.CatalogService")).isEqualTo("my-very-catalog");
        assertThat(LOOSE.apply("io.neonbee.test.TestService1")).isEqualTo("io-neonbee-test-test-service1");
        assertThat(LOOSE.apply("Frontend.Service")).isEqualTo("frontend");
    }

    private Future<HttpResponse<Buffer>> requestMetadata(String namespace) {
        FullQualifiedName fqn = new FullQualifiedName(namespace, "WillBeIgnored");
        return requestOData(new io.neonbee.test.base.ODataRequest(fqn).setMetadata());
    }

    private static final Consumer<Buffer> ASSERT_HANDLER_TS1 =
            body -> assertThat(body.toString()).contains("Namespace=\"io.neonbee.handler.TestService\"");

    private static final Consumer<Buffer> ASSERT_HANDLER_TS2 =
            body -> assertThat(body.toString()).contains("Namespace=\"io.neonbee.handler2.Test2Service\"");

    private static final Consumer<Buffer> ASSERT_HANDLER_TS3 =
            body -> assertThat(body.toString()).contains("Namespace=\"Service\"");

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("check if multiple service endpoints are created with strict URI mapping")
    public void testStrictUriConversion(VertxTestContext testContext) {
        all(assertOData(requestMetadata("io.neonbee.handler.TestService"), ASSERT_HANDLER_TS1, testContext),
                assertOData(requestMetadata("io.neonbee.handler2.Test2Service"), ASSERT_HANDLER_TS2, testContext),
                assertOData(requestMetadata("Service"), ASSERT_HANDLER_TS3, testContext))
                        .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("check if loose URI mapping works")
    public void testLooseUriConversion(VertxTestContext testContext) {
        all(assertOData(requestMetadata("io-neonbee-handler-test"), ASSERT_HANDLER_TS1, testContext),
                assertOData(requestMetadata("io-neonbee-handler2-test2"), ASSERT_HANDLER_TS2, testContext),
                assertOData(requestMetadata("test-service3"), ASSERT_HANDLER_TS3, testContext))
                        .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("check if CDS URI mapping works")
    public void testCDSUriConversion(VertxTestContext testContext) {
        all(assertOData(requestMetadata("test"), ASSERT_HANDLER_TS1, testContext),
                assertOData(requestMetadata("test2"), ASSERT_HANDLER_TS2, testContext),
                assertOData(requestMetadata(""), ASSERT_HANDLER_TS3, testContext))
                        .onComplete(testContext.succeedingThenComplete());
    }
}
