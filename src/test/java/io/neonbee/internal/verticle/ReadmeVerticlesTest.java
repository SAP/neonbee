package io.neonbee.internal.verticle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.FullQualifiedName;

import io.neonbee.NeonBee;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataMap;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.DataVerticle;
import io.neonbee.entity.EntityVerticle;
import io.neonbee.entity.EntityWrapper;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

// @formatter:off
class DatabaseVerticle extends DataVerticle<JsonArray> {
    @Override
    public String getName() {
        return "Database";
    }

    @Override
    public Future<JsonArray> retrieveData(DataQuery query, DataContext context) {
        // return a future to a JsonArray here, e.g. by connecting to the database
        return Future.succeededFuture(new JsonArray()
            .add(new JsonObject().put("Id", 1).put("Name", "Customer A"))
            .add(new JsonObject().put("Id", 2).put("Name", "Customer B")));
    }
}

class ProcessingVerticle extends DataVerticle<JsonArray> {
    @Override
    public String getName() {
        return "CustomerData";
    }

    @Override
    public Future<Collection<DataRequest>> requireData(DataQuery query, DataContext context) {
        return Future.succeededFuture(List.of(new DataRequest("Database", query)));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Future<JsonArray> retrieveData(DataQuery query, DataMap require, DataContext context) {
        AsyncResult<JsonArray> databaseResult = require.<JsonArray>findFirst("Database").orElse(null);
        if (databaseResult.failed()) {
            // retrieving data from the database failed
            return Future.failedFuture(databaseResult.cause());
        }

        // reverse the database result or do any other more reasonable processing ;-)
        List<Object> reversedDatabaseResult = new ArrayList<>();
        new ArrayDeque<Object>(databaseResult.result().getList())
            .descendingIterator()
            .forEachRemaining(reversedDatabaseResult::add);

        return Future.succeededFuture(new JsonArray(reversedDatabaseResult));
    }
}

class CustomerEntityVerticle extends EntityVerticle {
    @Override
    public Future<Set<FullQualifiedName>> entityTypeNames() {
        return Future.succeededFuture(Set.of(new FullQualifiedName("MyModel", "Customers")));
    }

    @Override
    public Future<Collection<DataRequest>> requireData(DataQuery query, DataContext context) {
        return Future.succeededFuture(List.of(new DataRequest("CustomerData", query)));
    }

    @Override
    public Future<EntityWrapper> retrieveData(DataQuery query, DataMap require, DataContext context) {
        List<Entity> entities = new ArrayList<>();

        for (Object object : require.<JsonArray>resultFor("CustomerData")) {
            JsonObject jsonObject = (JsonObject) object;

            Entity entity = new Entity();
            entity.addProperty(new Property(null, "Id", ValueType.PRIMITIVE, jsonObject.getValue("Id")));
            entity.addProperty(new Property(null, "Name", ValueType.PRIMITIVE, jsonObject.getValue("Name")));
            entities.add(entity);
        }

        return Future.succeededFuture(new EntityWrapper(new FullQualifiedName("MyModel", "Customers"), entities));
    }
}

class OtherExamples {
    private Vertx vertx;
    private DataContext context;

    public void loggingExample() {
        LoggingFacade logger = LoggingFacade.create();

        logger.correlateWith(context).info("Hello NeonBee");
    }

    @SuppressWarnings("unused")
    public void neonbeeExamples() {
        NeonBee neonbee = NeonBee.get(vertx);

        neonbee.getOptions();

        neonbee.getLocalMap();
        neonbee.getAsyncMap();
    }
}
// @formatter:on

/*
 * No tests needed here. The test of this file is whether the verticle used in the readme compile.
 */
