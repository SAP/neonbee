package io.neonbee.data.internal.metrics;

import java.util.Collection;
import java.util.List;

import io.neonbee.NeonBeeDeployable;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataMap;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.DataVerticle;
import io.vertx.core.Future;

@NeonBeeDeployable(namespace = TestRequireDataVerticle.NAMESPACE)
public class TestRequireDataVerticle extends DataVerticle<String> {
    private static final String NAME = TestRequireDataVerticle.class.getSimpleName();

    public static final String NAMESPACE = "test";

    public static final String QUALIFIED_NAME = createQualifiedName(NAMESPACE, NAME);

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Future<Collection<DataRequest>> requireData(DataQuery query, DataContext context) {
        DataRequest request = new DataRequest(TestSourceDataVerticle.QUALIFIED_NAME);
        return Future.succeededFuture(List.of(request));
    }

    @Override
    public Future<String> retrieveData(DataQuery query, DataMap require, DataContext context) {
        String requiredContent = require.resultFor(TestSourceDataVerticle.QUALIFIED_NAME);
        return Future.succeededFuture(NAME + "[" + requiredContent + "]");
    }
}
