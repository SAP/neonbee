package io.neonbee.endpoint;

import static io.neonbee.endpoint.HttpMethodToDataActionMapper.mapMethodToAction;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HttpMethodToDataActionMapperTest {

    @Test
    void testMapMethodToAction() {
        assertEquals(io.neonbee.data.DataAction.CREATE, mapMethodToAction(io.vertx.core.http.HttpMethod.POST));
        assertEquals(io.neonbee.data.DataAction.READ, mapMethodToAction(io.vertx.core.http.HttpMethod.GET));
        assertEquals(io.neonbee.data.DataAction.READ, mapMethodToAction(io.vertx.core.http.HttpMethod.HEAD));
        assertEquals(io.neonbee.data.DataAction.UPDATE, mapMethodToAction(io.vertx.core.http.HttpMethod.PUT));
        assertEquals(io.neonbee.data.DataAction.UPDATE, mapMethodToAction(io.vertx.core.http.HttpMethod.PATCH));
        assertEquals(io.neonbee.data.DataAction.DELETE, mapMethodToAction(io.vertx.core.http.HttpMethod.DELETE));
        assertNull(mapMethodToAction(io.vertx.core.http.HttpMethod.OPTIONS));
    }
}
