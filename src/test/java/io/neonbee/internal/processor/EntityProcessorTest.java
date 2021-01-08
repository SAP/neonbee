package io.neonbee.internal.processor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.TimeUnit;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.server.api.ODataResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.neonbee.entity.EntityWrapper;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class EntityProcessorTest {
    private Promise<Void> processPromise;

    private EntityProcessor ep;

    private ODataResponse response;

    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp(Vertx vertx) {
        processPromise = mock(Promise.class);
        ep = new EntityProcessor(vertx, null, processPromise);
        response = mock(ODataResponse.class);
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Return 404 in case that no entity was found")
    public void handleReadEntityResultTest(Vertx vertx, VertxTestContext testCtx) {
        vertx.runOnContext(v -> testCtx.verify(() -> {
            ep.handleReadEntityResult(null, mock(EdmEntitySet.class), null, this.response, null, null)
                    .handle(Future.<EntityWrapper>succeededFuture(new EntityWrapper("My.Entity", (Entity) null)));

            verify(response).setStatusCode(404);
            verify(processPromise).complete();
            testCtx.completeNow();
        }));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Return 204 in case that a entity was deleted successfully")
    public void handleDeleteEntityResultTest(Vertx vertx, VertxTestContext testCtx) {
        vertx.runOnContext(v -> testCtx.verify(() -> {
            ep.handleDeleteEntityResult(response)
                    .handle(Future.<Object>succeededFuture(new EntityWrapper("My.Entity", (Entity) null)));

            verify(response).setStatusCode(204);
            verify(processPromise).complete();
            testCtx.completeNow();
        }));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Return 204 in case that a entity was created successfully")
    public void handleCreateEntityResultTest(Vertx vertx, VertxTestContext testCtx) {
        vertx.runOnContext(v -> testCtx.verify(() -> {
            ep.handleCreateEntityResult(response)
                    .handle(Future.<Object>succeededFuture(new EntityWrapper("My.Entity", (Entity) null)));

            verify(response).setStatusCode(204);
            verify(processPromise).complete();
            testCtx.completeNow();
        }));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Return 204 in case that a entity was updated successfully")
    public void handleUpdateEntityResultTest(Vertx vertx, VertxTestContext testCtx) {
        vertx.runOnContext(v -> testCtx.verify(() -> {
            ep.handleUpdateEntityResult(response)
                    .handle(Future.<Object>succeededFuture(new EntityWrapper("My.Entity", (Entity) null)));

            verify(response).setStatusCode(204);
            verify(processPromise).complete();
            testCtx.completeNow();
        }));
    }
}
