package io.neonbee.endpoint.odatav4;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.endpoint.odatav4.ODataV4Endpoint.CONFIG_URI_CONVERSION;
import static io.neonbee.endpoint.odatav4.ODataV4Endpoint.UriConversion.CDS;
import static io.neonbee.endpoint.odatav4.ODataV4Endpoint.UriConversion.LOOSE;
import static io.neonbee.endpoint.odatav4.ODataV4Endpoint.UriConversion.STRICT;
import static io.neonbee.internal.helper.CollectionHelper.multiMapToMap;
import static io.neonbee.internal.helper.StringHelper.EMPTY;
import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.vertx.core.CompositeFuture.all;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.neonbee.config.EndpointConfig;
import io.neonbee.config.ServerConfig;
import io.neonbee.data.DataAdapter;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataException;
import io.neonbee.data.DataQuery;
import io.neonbee.endpoint.odatav4.ODataV4Endpoint.UriConversion;
import io.neonbee.entity.EntityVerticle;
import io.neonbee.entity.EntityWrapper;
import io.neonbee.internal.verticle.ServerVerticle;
import io.neonbee.test.base.ODataEndpointTestBase;
import io.neonbee.test.base.ODataRequest;
import io.neonbee.test.helper.WorkingDirectoryBuilder;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Verticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;

class ODataV4EndpointTest extends ODataEndpointTestBase {

    private static final FullQualifiedName TEST_USERS = new FullQualifiedName("Service", "TestUsers");

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
            EndpointConfig epc = new EndpointConfig().setType(ODataV4Endpoint.class.getName())
                    .setAdditionalConfig(new JsonObject().put(CONFIG_URI_CONVERSION, uriConversion.toString()));
            ServerConfig sc = new ServerConfig(opts.getConfig()).setEndpointConfigs(List.of(epc));
            opts.setConfig(sc.toJson());
            WorkingDirectoryBuilder.writeDeploymentOptions(ServerVerticle.class, opts, root);
        });
    }

    @Test
    @DisplayName("check if (lazy) loading OData models on first request to ODataEndpoint works")
    void testODataEndpointLazyLoading(VertxTestContext testContext) {
        assertOData(requestMetadata("io.neonbee.handler.TestService"),
                body -> assertThat(body.toString()).contains("<edmx:Edmx"), testContext)
                        .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    void testUriConversionRules() {
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
        assertThat(CDS.apply("Service")).isEqualTo("");

        // special case, naming a service in a namespace only "Service" will result in the empty name for the service
        assertThat(CDS.apply("any.namespace.Service")).isEqualTo("");

        // some more real examples
        assertThat(LOOSE.apply("my.very.CatalogService")).isEqualTo("my-very-catalog");
        assertThat(LOOSE.apply("io.neonbee.test.TestService1")).isEqualTo("io-neonbee-test-test-service1");
        assertThat(LOOSE.apply("Frontend.Service")).isEqualTo("frontend");
        assertThat(LOOSE.apply("Service")).isEqualTo("");
    }

    private Future<HttpResponse<Buffer>> requestMetadata(String namespace) {
        FullQualifiedName fqn = new FullQualifiedName(namespace, "WillBeIgnored");
        return requestOData(new ODataRequest(fqn).setMetadata());
    }

    @Test
    @DisplayName("check if multiple service endpoints are created with strict URI mapping")
    void testStrictUriConversion(VertxTestContext testContext) {
        all(assertOData(requestMetadata("io.neonbee.handler.TestService"), ODataV4EndpointTest::assertTS1Handler,
                testContext),
                assertOData(requestMetadata("io.neonbee.handler2.Test2Service"), ODataV4EndpointTest::assertTS2Handler,
                        testContext),
                assertOData(requestMetadata("Service"), ODataV4EndpointTest::assertTS3Handler, testContext))
                        .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @DisplayName("check if loose URI mapping works")
    void testLooseUriConversion(VertxTestContext testContext) {
        all(assertOData(requestMetadata("io-neonbee-handler-test"), ODataV4EndpointTest::assertTS1Handler, testContext),
                assertOData(requestMetadata("io-neonbee-handler2-test2"), ODataV4EndpointTest::assertTS2Handler,
                        testContext),
                assertOData(requestMetadata(""), ODataV4EndpointTest::assertTS3Handler, testContext))
                        .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @DisplayName("check if CDS URI mapping works")
    void testCDSUriConversion(VertxTestContext testContext) {
        all(assertOData(requestMetadata("test"), ODataV4EndpointTest::assertTS1Handler, testContext),
                assertOData(requestMetadata("test2"), ODataV4EndpointTest::assertTS2Handler, testContext),
                assertOData(requestMetadata(""), ODataV4EndpointTest::assertTS3Handler, testContext))
                        .onComplete(testContext.succeedingThenComplete());
    }

    static Stream<Arguments> customStatusCodes() {
        return Stream.of(Arguments.of(HTTP_BAD_REQUEST), Arguments.of(HTTP_FORBIDDEN), Arguments.of(HTTP_NOT_FOUND),
                Arguments.of(INTERNAL_SERVER_ERROR.code()));
    }

    @ParameterizedTest(name = "{index}: with status code {0}")
    @MethodSource("customStatusCodes")
    @DisplayName("ODataEndpointHandler must forward custom status codes from DataExceptions to the client")
    void testHTTPExceptions(int statusCode, VertxTestContext testContext) {
        Verticle dummyVerticle =
                createDummyEntityVerticle(TEST_USERS).withDataAdapter(new DataAdapter<EntityWrapper>() {
                    @Override
                    public Future<EntityWrapper> retrieveData(DataQuery query, DataContext context) {
                        return Future.failedFuture(new DataException(statusCode));
                    }
                });

        deployVerticle(dummyVerticle).compose(v -> new ODataRequest(TEST_USERS).send(getNeonBee()))
                .onComplete(testContext.succeeding(resp -> {
                    testContext.verify(() -> assertThat(resp.statusCode()).isEqualTo(statusCode));
                    testContext.completeNow();
                }));
    }

    @Test
    @DisplayName("Query parameters should be decoded before being forwarded to an EntityVerticle")
    void testURLQueryDecoding(VertxTestContext testContext) {
        MultiMap query = MultiMap.caseInsensitiveMultiMap().add("$filter", "description eq ''");
        EntityVerticle dummy = createDummyEntityVerticle(TEST_USERS).withDynamicResponse((dataQuery, dataContext) -> {
            testContext.verify(() -> assertThat(dataQuery.getParameters()).isEqualTo(multiMapToMap(query)));
            testContext.completeNow();
            return new EntityWrapper(TEST_USERS, (Entity) null);
        });

        // requestOData encodes the query parameters before sending them to the ODataV4Endpoint
        deployVerticle(dummy).compose(v -> requestOData(new ODataRequest(TEST_USERS).setQuery(query)))
                .onComplete(testContext.succeeding(v -> {}));
    }

    @Test
    @DisplayName("Uri path on Entity request must start with a leading slash")
    void testURIPathExtraction(VertxTestContext testContext) {
        EntityVerticle dummy = createDummyEntityVerticle(TEST_USERS).withDynamicResponse((dataQuery, dataContext) -> {
            testContext.verify(() -> assertThat(dataQuery.getUriPath()).isEqualTo("/Service/TestUsers"));
            testContext.completeNow();
            return new EntityWrapper(TEST_USERS, (Entity) null);
        });

        deployVerticle(dummy).compose(v -> requestOData(new ODataRequest(TEST_USERS)))
                .onComplete(testContext.succeeding(v -> {}));
    }

    @Test
    @DisplayName("Test OData response hint")
    void testODataResponseHint(VertxTestContext testContext) {
        EntityVerticle dummy = createDummyEntityVerticle(TEST_USERS).withDynamicResponse((dataQuery, dataContext) -> {
            dataContext.responseData().put("OData.filter", Boolean.TRUE);
            return new EntityWrapper(TEST_USERS, (Entity) null);
        });

        deployVerticle(dummy).compose(v -> requestOData(new ODataRequest(TEST_USERS)))
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> testContext.completeNow())));
    }

    private static void assertTS1Handler(Buffer body) {
        assertThat(body.toString()).contains("Namespace=\"io.neonbee.handler.TestService\"");
    }

    private static void assertTS2Handler(Buffer body) {
        assertThat(body.toString()).contains("Namespace=\"io.neonbee.handler2.Test2Service\"");
    }

    private static void assertTS3Handler(Buffer body) {
        assertThat(body.toString()).contains("Namespace=\"Service\"");
    }

}
