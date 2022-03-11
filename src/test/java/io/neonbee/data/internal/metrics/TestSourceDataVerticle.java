package io.neonbee.data.internal.metrics;

import io.neonbee.NeonBeeDeployable;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataMap;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataVerticle;
import io.vertx.core.Future;

@NeonBeeDeployable(namespace = TestSourceDataVerticle.NAMESPACE)
public class TestSourceDataVerticle extends DataVerticle<String> {

    public static final String NAMESPACE = "test";

    private static final String NAME = TestSourceDataVerticle.class.getSimpleName();

    public static final String QUALIFIED_NAME = createQualifiedName(NAMESPACE, NAME);

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Future<String> retrieveData(DataQuery query, DataMap require, DataContext context) {
        return Future.succeededFuture(NAME + " content");
    }
}
