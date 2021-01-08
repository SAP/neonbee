package io.neonbee.test.helper;

import static io.vertx.core.Future.succeededFuture;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.FullQualifiedName;

import io.neonbee.data.DataAdapter;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataVerticle;
import io.neonbee.entity.EntityVerticle;
import io.neonbee.entity.EntityWrapper;
import io.vertx.core.Future;

public class DummyVerticleHelper {

    /**
     * Creates a factory which can be used to construct a dummy {@link DataVerticle}.
     *
     * @param fqn the full qualified name of the {@link DataVerticle}
     * @return a {@link DummyDataVerticleFactory} which provides methods to construct a {@link DataVerticle}.
     */
    public static DummyDataVerticleFactory createDummyDataVerticle(String fqn) {
        return new DummyDataVerticleFactory(fqn);
    }

    /**
     * Creates a factory which can be used to construct a dummy {@link EntityVerticle}.
     *
     * @param fqn the full qualified name of the {@link EntityVerticle}
     * @return a {@link DummyEntityVerticleFactory} which provides methods to construct an {@link EntityVerticle}.
     */
    public static DummyEntityVerticleFactory createDummyEntityVerticle(FullQualifiedName fqn) {
        return new DummyEntityVerticleFactory(fqn);
    }

    public static class DummyDataVerticleFactory {
        private final String fqn;

        private DummyDataVerticleFactory(String fqn) {
            this.fqn = fqn;
        }

        /**
         * Provides a {@link DataVerticle} which always responds with a static answer.
         *
         * @param response the static data response to be returned from the {@link DataVerticle}
         * @param <T>      the type of the data response
         * @return a {@link DataVerticle} of generic type T which returns the static response value.
         */
        public <T> DataVerticle<T> withStaticResponse(T response) {
            return withDynamicResponse((query, context) -> response);
        }

        /**
         * Provides a {@link DataVerticle} that responds with a dynamic value that is defined within the passed
         * {@link BiFunction}.
         *
         * @param response the logic to build a dynamic response based on the {@link DataQuery} and {@link DataContext}
         * @param <T>      the type of the data response
         * @return a {@link DataVerticle} of generic type T which returns a dynamic response based on the
         *         {@link DataQuery} and {@link DataContext}
         */
        public <T> DataVerticle<T> withDynamicResponse(BiFunction<DataQuery, DataContext, T> response) {
            return withDataAdapter(new DataAdapter<>() {

                @Override
                public Future<T> retrieveData(DataQuery query, DataContext context) {
                    return succeededFuture(response.apply(query, context));
                }
            });
        }

        /**
         * Provides a {@link DataVerticle} that responds with a dynamic exception that is defined within the passed
         * {@link BiFunction}.
         *
         * @param response the logic to build a dynamic response based on the {@link DataQuery} and {@link DataContext}
         * @return a {@link DataVerticle} of Object which returns a dynamic response based on the {@link DataQuery} and
         *         {@link DataContext}
         */
        public DataVerticle<?> withDynamicException(BiFunction<DataQuery, DataContext, Exception> response) {
            return withDataAdapter(new DataAdapter<>() {

                @Override
                public Future<Object> retrieveData(DataQuery query, DataContext context) {
                    return Future.failedFuture(response.apply(query, context));
                }
            });
        }

        /**
         * Provides a {@link DataVerticle} that responds based on the passed {@link DataAdapter}. This method allows to
         * mock READ and WRITE requests.
         *
         * @param dataAdapter an implementation of the {@link DataAdapter}
         * @param <T>         the type of the data response
         * @return a {@link DataVerticle} of generic type T which returns a dynamic response based on the implementation
         *         of the passed {@link DataAdapter}.
         */
        public <T> DataVerticle<T> withDataAdapter(DataAdapter<T> dataAdapter) {
            int beginNameIdx = fqn.lastIndexOf('/');

            DataVerticle<T> dummyVerticle = new DataVerticle<>() {

                @Override
                public Future<T> createData(DataQuery query, DataContext context) {
                    return dataAdapter.createData(query, context);
                }

                @Override
                public Future<T> retrieveData(DataQuery query, DataContext context) {
                    return dataAdapter.retrieveData(query, context);
                }

                @Override
                public Future<T> deleteData(DataQuery query, DataContext context) {
                    return dataAdapter.deleteData(query, context);
                }

                @Override
                public Future<T> updateData(DataQuery query, DataContext context) {
                    return dataAdapter.updateData(query, context);
                }

                @Override
                public String getName() {
                    return beginNameIdx <= 0 ? fqn : fqn.substring(beginNameIdx + 1);
                }
            };

            try {
                Supplier<String> dummySupplier = () -> beginNameIdx <= 0 ? null : fqn.substring(0, beginNameIdx);
                ReflectionHelper.setValueOfPrivateField(dummyVerticle, "namespaceSupplier", dummySupplier);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return dummyVerticle;
        }
    }

    public static class DummyEntityVerticleFactory {
        private final FullQualifiedName fqn;

        private DummyEntityVerticleFactory(FullQualifiedName fqn) {
            this.fqn = fqn;
        }

        /**
         * Provides an {@link EntityVerticle} which always responds with a static answer.
         *
         * @param response the static entity response to be returned from the {@link EntityVerticle}
         * @return an {@link EntityVerticle} which returns the static response value.
         */
        public EntityVerticle withStaticResponse(Entity response) {
            return withStaticResponse(Optional.ofNullable(response).map(r -> List.of(r)).orElse(List.of()));
        }

        /**
         * Provides an {@link EntityVerticle} which always responds with a static answer.
         *
         * @param response the static entity response to be returned from the {@link EntityVerticle}
         * @return an {@link EntityVerticle} which returns the static response value.
         */
        public EntityVerticle withStaticResponse(List<Entity> response) {
            return withDynamicResponse((query, context) -> new EntityWrapper(fqn, response));
        }

        /**
         * Provides an {@link EntityVerticle} that responds with a dynamic value that is defined within the passed
         * {@link BiFunction}.
         *
         * @param response the logic to build a dynamic response based on the {@link DataQuery} and {@link DataContext}
         * @return an {@link EntityVerticle} which returns a dynamic response based on the {@link DataQuery} and
         *         {@link DataContext}
         */
        public EntityVerticle withDynamicResponse(BiFunction<DataQuery, DataContext, EntityWrapper> response) {
            return withDataAdapter(new DataAdapter<>() {

                @Override
                public Future<EntityWrapper> retrieveData(DataQuery query, DataContext context) {
                    return succeededFuture(response.apply(query, context));
                }
            });
        }

        /**
         * Provides an {@link EntityVerticle} that responds with a dynamic exception that is defined within the passed
         * {@link BiFunction}.
         *
         * @param response the logic to build a dynamic response based on the {@link DataQuery} and {@link DataContext}
         * @return an {@link EntityVerticle} which returns a dynamic response based on the {@link DataQuery} and
         *         {@link DataContext}
         */
        public EntityVerticle withDynamicException(BiFunction<DataQuery, DataContext, Exception> response) {
            return withDataAdapter(new DataAdapter<>() {

                @Override
                public Future<EntityWrapper> retrieveData(DataQuery query, DataContext context) {
                    return Future.failedFuture(response.apply(query, context));
                }
            });
        }

        /**
         * Provides an {@link EntityVerticle} that responds based on the passed {@link DataAdapter}. This method allows
         * to mock READ and WRITE requests.
         *
         * @param dataAdapter an implementation of the {@link DataAdapter}
         * @return an {@link EntityVerticle} which returns a dynamic response based on the implementation of the passed
         *         {@link DataAdapter}.
         */
        public EntityVerticle withDataAdapter(DataAdapter<EntityWrapper> dataAdapter) {
            return new EntityVerticle() {

                @Override
                public Future<EntityWrapper> retrieveData(DataQuery query, DataContext context) {
                    return dataAdapter.retrieveData(query, context);
                }

                @Override
                public Future<EntityWrapper> createData(DataQuery query, DataContext context) {
                    return dataAdapter.createData(query, context);
                }

                @Override
                public Future<EntityWrapper> updateData(DataQuery query, DataContext context) {
                    return dataAdapter.updateData(query, context);
                }

                @Override
                public Future<EntityWrapper> deleteData(DataQuery query, DataContext context) {
                    return dataAdapter.deleteData(query, context);
                }

                @Override
                public Future<Set<FullQualifiedName>> entityTypeNames() {
                    return succeededFuture(Set.of(fqn));
                }
            };
        }
    }
}
