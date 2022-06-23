package io.neonbee.endpoint.odatav4.internal.olingo.processor;

import static com.google.common.truth.Truth.assertThat;
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
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.impl.HttpServerRequestInternal;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.RouterImpl;
import io.vertx.ext.web.impl.RoutingContextImpl;

class ProcessorHelperTest {

    @Test
    @DisplayName("Response hints should be transferred to routing context")
    void transferResponseHint() {
        HttpServerRequest request = Mockito.mock(HttpServerRequestInternal.class);
        Mockito.when(request.path()).thenReturn("/path");
        RouterImpl router = Mockito.mock(RouterImpl.class);
        Mockito.when(router.getAllowForward()).thenReturn(null);
        RoutingContext routingContext = new RoutingContextImpl(null, router, request, Set.of());
        DataContext dataContext = new DataContextImpl();
        dataContext.responseData().put(ODATA_FILTER_KEY, Boolean.TRUE);
        dataContext.responseData().put(ODATA_EXPAND_KEY, Boolean.FALSE);
        ProcessorHelper.transferResponseHint(dataContext, routingContext);
        assertThat(routingContext.<Boolean>get(RESPONSE_HEADER_PREFIX + ODATA_FILTER_KEY)).isTrue();
        assertThat(routingContext.<Boolean>get(RESPONSE_HEADER_PREFIX + ODATA_EXPAND_KEY)).isFalse();
        assertThat(routingContext.<Boolean>get(RESPONSE_HEADER_PREFIX + ODATA_SKIP_KEY)).isNull();
        assertThat(routingContext.<Boolean>get(RESPONSE_HEADER_PREFIX + ODATA_TOP_KEY)).isNull();
    }
}
