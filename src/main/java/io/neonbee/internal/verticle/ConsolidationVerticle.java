package io.neonbee.internal.verticle;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.FullQualifiedName;

import io.neonbee.NeonBeeDeployable;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataMap;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.DataVerticle;
import io.neonbee.entity.EntityVerticle;
import io.neonbee.entity.EntityWrapper;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;

@NeonBeeDeployable(namespace = NeonBeeDeployable.NEONBEE_NAMESPACE, autoDeploy = false)
public class ConsolidationVerticle extends DataVerticle<EntityWrapper> {
    /**
     * This header is used to store the entity name to consolidate.
     */
    public static final String ENTITY_TYPE_NAME_HEADER = "entityTypeName";

    private static final String NAME = "_consolidationVerticle";

    public static final String QUALIFIED_NAME =
            DataVerticle.createQualifiedName(NeonBeeDeployable.NEONBEE_NAMESPACE, NAME);

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Future<Collection<DataRequest>> requireData(DataQuery query, DataContext context) {
        return EntityVerticle
                .getVerticlesForEntityType(vertx, new FullQualifiedName(query.getHeader(ENTITY_TYPE_NAME_HEADER)))
                .map(qualifiedNames -> qualifiedNames.stream()
                        .map(qualifiedName -> new DataRequest(qualifiedName, query)).collect(Collectors.toList()));
    }

    @Override
    public Future<EntityWrapper> retrieveData(DataQuery query, DataMap require, DataContext context) {
        FullQualifiedName entityTypeName = new FullQualifiedName(query.getHeader(ENTITY_TYPE_NAME_HEADER));
        List<Entity> entities = new ArrayList<>();

        for (AsyncResult<?> asyncResult : require.values()) {
            if (asyncResult.failed()) {
                // do a lazy consolidation here, so do not fail in case one "backend" fails
                // TODO make consolidation strategy configurable (lazy vs. strict)
                // TODO log: could not receive data to consolidate
                continue;
            }

            Object object = asyncResult.result();
            if (!(object instanceof EntityWrapper)) {
                return failedFuture(new IllegalStateException(
                        "Entity verticle for consolidation are supposed to return entity wrappers"));
            }

            EntityWrapper entityWrapper = (EntityWrapper) object;
            if (!entityTypeName.equals(entityWrapper.getTypeName())) {
                return failedFuture(new IllegalStateException(
                        "Cannot consolidate entities of different types into one entity collection"));
            }

            entities.addAll(entityWrapper.getEntities());
        }

        // TODO: Add sorting / maybe filtering / skip & top handling
        return succeededFuture(new EntityWrapper(entityTypeName, entities));
    }
}
