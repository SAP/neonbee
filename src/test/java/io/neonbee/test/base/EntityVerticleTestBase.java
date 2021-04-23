package io.neonbee.test.base;

import java.nio.file.Path;
import java.util.List;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.junit.jupiter.api.TestInfo;

import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.entity.EntityVerticle;
import io.neonbee.entity.EntityWrapper;
import io.neonbee.test.helper.EntityResponseVerifier;
import io.neonbee.test.helper.WorkingDirectoryBuilder;
import io.vertx.core.Future;
import io.vertx.junit5.VertxTestContext;

public abstract class EntityVerticleTestBase extends NeonBeeTestBase implements EntityResponseVerifier {
    /**
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
     * Request data from a {@link EntityVerticle}.
     *
     * @param entity The entity represented by a {@link FullQualifiedName} to request the related
     *               {@link EntityVerticle}.
     *
     * @return A succeeded future with the response, or a failed future with the cause.
     */
    public Future<EntityWrapper> requestEntity(FullQualifiedName entity) {
        return requestEntity(new DataRequest(entity, new DataQuery()));
    }

    /**
     * Request data from a {@link EntityVerticle}.
     *
     * @param dataRequest The {@link DataRequest} to the {@link EntityVerticle}.
     *
     * @return A succeeded future with the response, or a failed future with the cause.
     */
    public Future<EntityWrapper> requestEntity(DataRequest dataRequest) {
        return requestEntity(dataRequest, new DataContextImpl());
    }

    /**
     * Request data from a {@link EntityVerticle}.
     *
     * @param dataRequest The {@link DataRequest} to the {@link EntityVerticle}.
     * @param dataContext The {@link DataContext} to be used for the request.
     *
     * @return A succeeded future with the response, or a failed future with the cause.
     */
    public Future<EntityWrapper> requestEntity(DataRequest dataRequest, DataContext dataContext) {
        return EntityVerticle.requestEntity(getNeonBee().getVertx(), dataRequest, dataContext);
    }
}
