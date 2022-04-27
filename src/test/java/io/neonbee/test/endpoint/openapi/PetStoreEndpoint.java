package io.neonbee.test.endpoint.openapi;

import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;
import static io.vertx.core.Future.succeededFuture;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import io.neonbee.config.EndpointConfig;
import io.neonbee.endpoint.openapi.AbstractOpenAPIEndpoint;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.openapi.RouterBuilder;
import io.vertx.ext.web.validation.RequestParameters;
import io.vertx.ext.web.validation.ValidationHandler;

public class PetStoreEndpoint extends AbstractOpenAPIEndpoint {

    /**
     * The default path the raw endpoint is exposed by NeonBee.
     */
    public static final String DEFAULT_BASE_PATH = "/petstore/";

    private static final Path CONTRACT_PATH = TEST_RESOURCES.resolveRelated("petstore.json");

    private final Map<String, JsonObject> pets = new HashMap<>();

    @Override
    public EndpointConfig getDefaultConfig() {
        return new EndpointConfig().setType(PetStoreEndpoint.class.getName()).setBasePath(DEFAULT_BASE_PATH);
    }

    @Override
    protected Future<String> getOpenAPIContractURL(Vertx vertx, JsonObject config) {
        return succeededFuture(CONTRACT_PATH.toString());
    }

    @Override
    protected Future<Router> createRouter(Vertx vertx, RouterBuilder routerBuilder) {
        routerBuilder.operation("createPets").handler(ctx -> {
            RequestParameters params = ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY);
            String name = params.body().getJsonObject().getString("name");

            int id = pets.size() + 1;
            JsonObject pet = new JsonObject().put("id", id).put("name", name);
            pets.put(pet.getInteger("id").toString(), pet);

            ctx.response().setStatusCode(201).end();
        });

        routerBuilder.operation("listPets").handler(ctx -> {
            JsonArray response = new JsonArray();
            pets.values().forEach(response::add);
            ctx.response().putHeader("Content-Type", "application/json").end(response.toBuffer());
        });

        routerBuilder.operation("showPetById").handler(ctx -> {
            RequestParameters params = ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY);
            String petId = params.pathParameter("petId").getString();

            JsonObject pet = pets.get(petId);
            if (pet == null) {
                ctx.response().setStatusCode(404).setStatusMessage("No pet found with id: " + petId).end();
            } else {
                ctx.response().putHeader("Content-Type", "application/json");
                ctx.response().end(pet.toBuffer());
            }
        });

        return succeededFuture(routerBuilder.createRouter());
    }
}
