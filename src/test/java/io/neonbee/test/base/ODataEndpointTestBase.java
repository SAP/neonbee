package io.neonbee.test.base;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.TestInfo;

import io.neonbee.test.helper.ODataResponseVerifier;
import io.neonbee.test.helper.WorkingDirectoryBuilder;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;

public abstract class ODataEndpointTestBase extends NeonBeeTestBase implements ODataResponseVerifier {

    /**
     * This method provides the data models that will be available when NeonBee is started.
     *
     * @return A list of {@link Path paths} to the models to be provided in this test.
     */
    protected abstract List<Path> provideEntityModels();

    @Override
    protected WorkingDirectoryBuilder provideWorkingDirectoryBuilder(TestInfo testInfo, VertxTestContext testContext) {
        WorkingDirectoryBuilder dirBuilder = WorkingDirectoryBuilder.standard();
        provideEntityModels().forEach(dirBuilder::addModel);
        return dirBuilder;
    }

    /**
     * This method can be used to request data from an HTTP endpoint serving OData based on a passed OData request.
     *
     * @param request The OData request which will be sent
     * @return A future which contains the response
     */
    public Future<HttpResponse<Buffer>> requestOData(AbstractODataRequest<?> request) {
        return request.send(getNeonBee());
    }
}
