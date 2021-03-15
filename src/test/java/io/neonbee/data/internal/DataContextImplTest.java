package io.neonbee.data.internal;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.handler.CorrelationIdHandler.CORRELATION_ID;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Iterators;
import com.google.common.collect.Streams;

import io.neonbee.data.DataContext;
import io.neonbee.data.DataContext.DataVerticleCoordinate;
import io.neonbee.data.DataException;
import io.neonbee.data.internal.DataContextImpl.DataVerticleCoordinateImpl;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;

class DataContextImplTest {
    DataContextImpl context;

    @BeforeEach
    void setUp() {
        context = new DataContextImpl("correlationId", "bearerToken", new JsonObject().put("username", "Duke"), null,
                null);
    }

    @Test
    void testEncodeContextToString() {
        context.pushVerticleToPath("Data1Verticle");
        context.amendTopVerticleCoordinate("deploymentId1");
        context.pushVerticleToPath("Data2Verticle");
        context.amendTopVerticleCoordinate("deploymentId2");
        String json = DataContextImpl.encodeContextToString(context);
        JsonObject jsonObject = new JsonObject(json);
        assertThat(jsonObject.getJsonArray("path")).hasSize(2);
    }

    @Test
    void testDecodeContextFromString() {
        String json =
                "{\"correlationId\":\"correlationId\",\"bearerToken\":\"bearerToken\",\"userPrincipal\":{\"username\":\"Duke\"},\"data\":{},\"path\":[{\"qualifiedName\":\"Data1Verticle\",\"deploymentId\":\"deploymentId1\",\"ipAddress\":\"ip1\"},{\"qualifiedName\":\"Data2Verticle\",\"deploymentId\":\"deploymentId2\",\"ipAddress\":\"ip2\"}]}";
        DataContext context2 = DataContextImpl.decodeContextFromString(json);
        assertEquals(context2.correlationId(), "correlationId");
        assertEquals(context2.bearerToken(), "bearerToken");
        assertEquals(context2.userPrincipal(), new JsonObject().put("username", "Duke"));
        assertEquals(2, contextPathSize(context2));

        Iterator<DataVerticleCoordinate> path = context2.path();
        DataVerticleCoordinate coordinate = path.next();
        assertThat(coordinate.getQualifiedName()).isEqualTo("Data1Verticle");
        assertThat(coordinate.getDeploymentId()).isEqualTo("deploymentId1");
        assertThat(coordinate.getIpAddress()).isEqualTo("ip1");

        coordinate = path.next();
        assertThat(coordinate.getQualifiedName()).isEqualTo("Data2Verticle");
        assertThat(coordinate.getDeploymentId()).isEqualTo("deploymentId2");
        assertThat(coordinate.getIpAddress()).isEqualTo("ip2");
    }

    @Test
    void testAddVerticleToPath() {
        context.pushVerticleToPath("DataVerticle");
        DataVerticleCoordinate coordinate = context.path().next();

        assertEquals(1, contextPathSize(context));
        assertEquals("DataVerticle", coordinate.getQualifiedName());
        assertNull(coordinate.getDeploymentId());
    }

    @Test
    void testAmendVerticleCoordinate() {
        context.pushVerticleToPath("DataVerticle");
        context.amendTopVerticleCoordinate("deploymentId");
        DataVerticleCoordinate coordinate = context.path().next();

        assertEquals(1, contextPathSize(context));
        assertEquals("DataVerticle", coordinate.getQualifiedName());
        assertEquals("deploymentId", coordinate.getDeploymentId());
    }

    @Test
    void testRemoveVerticleFromPath() {
        context.pushVerticleToPath("DataVerticle");
        context.pushVerticleToPath("Data1Verticle");
        assertEquals(2, contextPathSize(context));
        context.popVerticleFromPath();
        assertEquals(1, contextPathSize(context));
    }

    @Test
    void testPaths() {
        context.pushVerticleToPath("DataVerticle");
        context.pushVerticleToPath("Data1Verticle");
        assertEquals(2, contextPathSize(context));
    }

    @Test
    void testPathAsString() {
        context.pushVerticleToPath("Data1Verticle");
        context.amendTopVerticleCoordinate("deploymentId1");
        context.pushVerticleToPath("Data2Verticle");
        context.amendTopVerticleCoordinate("deploymentId2");
        assertEquals(2, context.pathAsString().split(System.lineSeparator()).length);
    }

    @Test
    void testUpdateTimestamp() {
        context.pushVerticleToPath("DataVerticle");
        context.amendTopVerticleCoordinate("deploymentId1");
        context.updateResponseTimestamp();
    }

    @Test
    void testPathStackHandling() {
        context.pushVerticleToPath("A");
        context.amendTopVerticleCoordinate("A-ID");
        context.pushVerticleToPath("B");
        context.amendTopVerticleCoordinate("B-ID");
        assertEquals(2, contextPathSize(context));

        List<DataVerticleCoordinate> paths = Streams.stream(context.path()).collect(Collectors.toList());
        assertThat(paths).hasSize(2);
        assertThat(paths.get(0).getQualifiedName()).isEqualTo("A");
        assertThat(paths.get(1).getQualifiedName()).isEqualTo("B");

        context.pushVerticleToPath("C");
        String contextString = DataContextImpl.encodeContextToString(context);
        DataContextImpl context2 = (DataContextImpl) DataContextImpl.decodeContextFromString(contextString);
        context2.amendTopVerticleCoordinate("C-ID");
        paths = Streams.stream(context.path()).collect(Collectors.toList());
        assertThat(paths).hasSize(3);
        assertThat(paths.get(0).getQualifiedName()).isEqualTo("A");
        assertThat(paths.get(1).getQualifiedName()).isEqualTo("B");
        assertThat(paths.get(2).getQualifiedName()).isEqualTo("C");
    }

    /*
     * There are some special cases were the DataContextImpl's 'path' list can be empty e.g. when running Unit tests.
     * Therefore no (unhandled) exceptions should be thrown when working with this list.
     */
    @Test
    void testSpecialCasesWerePathsList() {
        DataContextImpl initialContext = new DataContextImpl("correlationId", "bearerToken",
                new JsonObject().put("username", "Duke"), Map.of(), null);
        assertThat(initialContext.path().hasNext()).isFalse();

        assertDoesNotThrow(() -> initialContext.amendTopVerticleCoordinate("deploymentId1"));
        assertThrows(NoSuchElementException.class, () -> initialContext.popVerticleFromPath());
    }

    @Test
    @DisplayName("DataVerticleCoordinate toString")
    void testToString() {
        DataVerticleCoordinateImpl coordinate = new DataVerticleCoordinateImpl("DataVerticle")
                .setDeploymentId("deploymentid").setIpAddress("ipAddress");
        assertThat(coordinate.toString())
                .matches("\\d{2}:\\d{2}:\\d{2}.\\d{4,} DataVerticle\\[deploymentid\\]@ipAddress");

        coordinate.updateResponseTimestamp();
        assertThat(coordinate.toString()).matches(
                "\\d{2}:\\d{2}:\\d{2}.\\d{4,} DataVerticle\\[deploymentid\\]@ipAddress \\d{2}:\\d{2}:\\d{2}.\\d{4,}");
    }

    @Test
    void testCopy() {
        DataContext copy = context.copy();
        assertNotSame(copy, context);
        assertNotSame(copy.path(), context.path());
    }

    @Test
    void testSelfCalling() {
        context.pushVerticleToPath("Data1Verticle");
        assertThrows(DataException.class, () -> {
            context.pushVerticleToPath("Data1Verticle");
        });
    }

    @Test
    @DisplayName("set path")
    void testSetPath() {
        context.pushVerticleToPath("1");
        context.pushVerticleToPath("2");
        context.pushVerticleToPath("3");
        List<DataVerticleCoordinate> snapshot = new ArrayList<>();
        context.path().forEachRemaining(snapshot::add);
        assertThat(snapshot.get(0).getQualifiedName()).isEqualTo("1");
        assertThat(snapshot.get(1).getQualifiedName()).isEqualTo("2");
        assertThat(snapshot.get(2).getQualifiedName()).isEqualTo("3");

        DataContextImpl context2 = new DataContextImpl(context);
        snapshot.clear();
        context2.path().forEachRemaining(snapshot::add);
        assertThat(snapshot.get(0).getQualifiedName()).isEqualTo("1");
        assertThat(snapshot.get(1).getQualifiedName()).isEqualTo("2");
        assertThat(snapshot.get(2).getQualifiedName()).isEqualTo("3");
    }

    @Test
    @DisplayName("Basic methods")
    void testVerticleCoordinate() {
        DataVerticleCoordinateImpl coordinate = new DataVerticleCoordinateImpl("qualifiedName")
                .setIpAddress("ipAddress").setDeploymentId("instanceId").updateResponseTimestamp();
        assertEquals("instanceId", coordinate.getDeploymentId());
        assertEquals("ipAddress", coordinate.getIpAddress());
        assertEquals("qualifiedName", coordinate.getQualifiedName());
        assertNotNull(coordinate.getRequestTimestamp());
        assertNotNull(coordinate.getResponseTimestamp());
        assertThat(coordinate.toString()).contains("qualifiedName[instanceId]@ipAddress");
    }

    @Test
    @DisplayName("it should be fine to create a empty context")
    void testNewContextConstructor() {
        testEmptyContext(new DataContextImpl());
    }

    @Test
    @DisplayName("it should be fine to create a empty context with null values for the constructor")
    void testNullValueContextConstructor() {
        testEmptyContext(new DataContextImpl(null, null, null, null));
    }

    private void testEmptyContext(DataContext context) {
        assertThat(context.correlationId()).isNull();
        assertThat(context.userPrincipal()).isNull();
        assertThat(context.data()).isNotNull();
        assertThat(context.data()).isEmpty();
    }

    @Test
    @DisplayName("the context should accept any correlation id (incl. null)")
    void testCorrelationId() {
        assertThat(new DataContextImpl("expected1", null, null, null).correlationId()).isEqualTo("expected1");
        assertThat(new DataContextImpl("expected2", null, null, null).correlationId()).isEqualTo("expected2");
        assertThat(new DataContextImpl(null, null, null, null).correlationId()).isNull();
    }

    @Test
    @DisplayName("the context should accept any bearer token (incl. null)")
    void testBearerToken() {
        assertThat(new DataContextImpl(null, "expected1", null, null).bearerToken()).isEqualTo("expected1");
        assertThat(new DataContextImpl(null, "expected2", null, null).bearerToken()).isEqualTo("expected2");
        assertThat(new DataContextImpl(null, null, null, null).bearerToken()).isNull();
    }

    @Test
    @DisplayName("the context should accept any user principal (incl. null)")
    void testUserPrincipal() {
        assertThat(new DataContextImpl(null, null, new JsonObject(), null).userPrincipal())
                .isInstanceOf(JsonObject.class);
        assertThat(new DataContextImpl(null, null, new JsonObject().put("anyAttribute", "anyExpectedValue"), null)
                .userPrincipal().getString("anyAttribute")).isEqualTo("anyExpectedValue");
        assertThat(new DataContextImpl(null, null, null, null).userPrincipal()).isNull();
    }

    @Test
    @DisplayName("the user principal of a context should be immutable")
    void testUserPrincipalImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> new DataContextImpl(null, null, new JsonObject(), null)
                .userPrincipal().put("anyAttribute", "anyValue"));
        assertThrows(UnsupportedOperationException.class,
                () -> new DataContextImpl(null, null, new JsonObject(), null).userPrincipal().remove("anyAttribute"));
    }

    @Test
    @DisplayName("the user arbitrary data of a context")
    void testArbitraryData() {
        assertThat(new DataContextImpl(null, null, null, null).data()).isEmpty();
        // should create a mutable map
        assertDoesNotThrow(() -> new DataContextImpl(null, null, null, Collections.emptyMap()).data()
                .put("anyAttribute", "anyValue"));
        assertThat(
                new DataContextImpl(null, null, null, Collections.singletonMap("expectedKey", "expectedValue")).data())
                        .containsExactly("expectedKey", "expectedValue");
        assertThat(new DataContextImpl(null, null, null, Collections.singletonMap("expectedKey", "expectedValue"))
                .setData(null).data()).isEmpty();
    }

    @Test
    @DisplayName("test passing a routing context instead")
    void testWithRoutingContext() {
        RoutingContext routingContextMock = mock(RoutingContext.class);
        HttpServerRequest requestMock = mock(HttpServerRequest.class);
        User userMock = mock(User.class);

        when(requestMock.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer anyExpectedToken123");
        when(userMock.principal()).thenReturn(new JsonObject().put("expectedKey", "expectedValue"));
        when(routingContextMock.get(CORRELATION_ID)).thenReturn("expectedCorrId");
        when(routingContextMock.user()).thenReturn(userMock);
        when(routingContextMock.request()).thenReturn(requestMock);

        DataContextImpl dataContext = new DataContextImpl(routingContextMock);
        assertThat(dataContext.correlationId()).isEqualTo("expectedCorrId");
        assertThat(dataContext.bearerToken()).isEqualTo("anyExpectedToken123");
        assertThat(dataContext.userPrincipal().getString("expectedKey")).isEqualTo("expectedValue");
    }

    @Test
    @DisplayName("test encoding / decoding null context")
    void testNullEncodeDecode() {
        DataContext dataContext = DataContextImpl.decodeContextFromString(
                DataContextImpl.encodeContextToString(new DataContextImpl(null, null, null, null)));
        assertThat(dataContext.correlationId()).isNull();
        assertThat(dataContext.bearerToken()).isNull();
        assertThat(dataContext.userPrincipal()).isNull();
        assertThat(dataContext.data()).isEmpty();
    }

    @Test
    @DisplayName("test encoding / decoding context")
    void testEncodeDecode() {
        DataContext dataContext = DataContextImpl.decodeContextFromString(DataContextImpl.encodeContextToString(
                new DataContextImpl("expected1", "expected2", new JsonObject().put("expectedKey", "expectedValue"),
                        new JsonObject().put("expected1", "expected2").put("expectedArray", new JsonArray().add(0))
                                .put("expectedNull", (Object) null).getMap())));
        assertThat(dataContext.correlationId()).isEqualTo("expected1");
        assertThat(dataContext.bearerToken()).isEqualTo("expected2");
        assertThat(dataContext.userPrincipal()).isEqualTo(new JsonObject().put("expectedKey", "expectedValue"));
        assertThat(new JsonObject(dataContext.data())).isEqualTo(new JsonObject().put("expected1", "expected2")
                .put("expectedArray", new JsonArray().add(0)).put("expectedNull", (Object) null));
    }

    private int contextPathSize(DataContext context) {
        return Iterators.size(context.path());
    }
}
