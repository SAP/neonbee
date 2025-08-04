package io.neonbee.endpoint.odatav4.internal.olingo.processor;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.endpoint.odatav4.internal.olingo.processor.ProcessorHelper.ODATA_COUNT_SIZE_KEY;
import static io.neonbee.endpoint.odatav4.internal.olingo.processor.ProcessorHelper.ODATA_EXPAND_KEY;
import static io.neonbee.endpoint.odatav4.internal.olingo.processor.ProcessorHelper.ODATA_FILTER_KEY;
import static io.neonbee.endpoint.odatav4.internal.olingo.processor.ProcessorHelper.ODATA_SKIP_KEY;
import static io.neonbee.endpoint.odatav4.internal.olingo.processor.ProcessorHelper.ODATA_TOP_KEY;
import static io.neonbee.endpoint.odatav4.internal.olingo.processor.ProcessorHelper.RESPONSE_HEADER_PREFIX;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.neonbee.data.DataContext;
import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.entity.EntityWrapper;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.impl.HttpServerRequestInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.RouterImpl;
import io.vertx.ext.web.impl.RoutingContextImpl;

class ProcessorHelperTest {

    @Test
    @DisplayName("Response hints should be transferred to routing context")
    void transferResponseHint() {
        HttpServerRequest request = Mockito.mock(HttpServerRequestInternal.class);
        Mockito.when(request.path()).thenReturn("/path");
        Mockito.when(request.authority()).thenReturn(Mockito.mock(HostAndPort.class));
        RouterImpl router = Mockito.mock(RouterImpl.class);
        Mockito.when(router.getAllowForward()).thenReturn(null);
        RoutingContext routingContext = new RoutingContextImpl(null, router, request, Set.of());
        DataContext dataContext = new DataContextImpl();
        dataContext.responseData().put(ODATA_FILTER_KEY, Boolean.TRUE);
        dataContext.responseData().put(ODATA_EXPAND_KEY, Boolean.FALSE);
        dataContext.responseData().put(ODATA_COUNT_SIZE_KEY, 42);
        EntityWrapper entityWrapper = Mockito.mock(EntityWrapper.class);
        EntityWrapper result = ProcessorHelper.transferResponseHint(dataContext, routingContext, entityWrapper);
        assertThat(routingContext.<Boolean>get(RESPONSE_HEADER_PREFIX + ODATA_FILTER_KEY)).isTrue();
        assertThat(routingContext.<Boolean>get(RESPONSE_HEADER_PREFIX + ODATA_EXPAND_KEY)).isFalse();
        assertThat(routingContext.<Boolean>get(RESPONSE_HEADER_PREFIX + ODATA_SKIP_KEY)).isNull();
        assertThat(routingContext.<Boolean>get(RESPONSE_HEADER_PREFIX + ODATA_TOP_KEY)).isNull();
        assertThat(routingContext.<Integer>get(RESPONSE_HEADER_PREFIX + ODATA_COUNT_SIZE_KEY)).isEqualTo(42);
        assertThat(result).isSameInstanceAs(entityWrapper);
    }

    @Test
    void enhanceDataContextWithRawBody() {
        // Create a mock RequestBody
        RequestBody requestBody = Mockito.mock(RequestBody.class);
        Buffer expectedBuffer = Buffer.buffer("Hello World");
        Mockito.when(requestBody.buffer()).thenReturn(expectedBuffer);

        DataContext dataContext = new DataContextImpl();
        ProcessorHelper.enhanceDataContextWithRawBody(requestBody, dataContext);
        assertThat(dataContext.get(DataContext.RAW_BODY_KEY).toString()).isEqualTo(expectedBuffer.toString());
    }

    @Test
    void enhanceDataContextWithRawBodyNull() {
        DataContext dataContext = new DataContextImpl();
        ProcessorHelper.enhanceDataContextWithRawBody(null, dataContext);
        Buffer o = dataContext.get(DataContext.RAW_BODY_KEY);

        assertThat(o).isNull();
    }
}
