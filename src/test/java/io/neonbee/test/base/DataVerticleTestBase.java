package io.neonbee.test.base;

import io.neonbee.data.DataContext;
import io.neonbee.data.DataRequest;
import io.neonbee.data.DataVerticle;
import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.test.helper.DataResponseVerifier;
import io.vertx.core.Future;

public class DataVerticleTestBase extends NeonBeeTestBase implements DataResponseVerifier {

    /**
     * Request data from a {@link DataVerticle}.
     *
     * @param qualifiedName The qualifiedName of the {@link DataVerticle} to request.
     * @param <T>           The type of the requested {@link DataVerticle}.
     *
     * @return A succeeded future with the response, or a failed future with the cause.
     */
    public <T> Future<T> requestData(String qualifiedName) {
        return requestData(new DataRequest(qualifiedName));
    }

    /**
     * Request data from a {@link DataVerticle}.
     *
     * @param dataRequest The {@link DataRequest} to the {@link DataVerticle}.
     * @param <T>         The type of the requested {@link DataVerticle}.
     *
     * @return A succeeded future with the response, or a failed future with the cause.
     */
    public <T> Future<T> requestData(DataRequest dataRequest) {
        return requestData(dataRequest, new DataContextImpl());
    }

    /**
     * Request data from a {@link DataVerticle}.
     *
     * @param dataRequest The {@link DataRequest} to the {@link DataVerticle}.
     * @param dataContext The {@link DataContext} to be used for the request.
     * @param <T>         The type of the requested {@link DataVerticle}.
     *
     * @return A succeeded future with the response, or a failed future with the cause.
     */
    public <T> Future<T> requestData(DataRequest dataRequest, DataContext dataContext) {
        return DataVerticle.requestData(getNeonBee().getVertx(), dataRequest, dataContext);
    }
}
