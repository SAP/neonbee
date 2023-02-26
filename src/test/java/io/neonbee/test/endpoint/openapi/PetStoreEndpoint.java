package io.neonbee.test.endpoint.openapi;

import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;
import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON;
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
import io.vertx.ext.web.openapi.router.RouterBuilder;
import io.vertx.openapi.contract.OpenAPIContract;
import io.vertx.openapi.validation.ValidatableResponse;

public class PetStoreEndpoint extends AbstractOpenAPIEndpoint {

    /**
     * The default path the raw endpoint is exposed by NeonBee.
     */
    public static final String DEFAULT_BASE_PATH = "/petstore/";

    private static final Path CONTRACT_PATH = TEST_RESOURCES.resolveRelated("petstore.json");

    private final Map<Integer, JsonObject> pets = new HashMap<>();

    @Override
    public EndpointConfig getDefaultConfig() {
        return new EndpointConfig().setType(PetStoreEndpoint.class.getName()).setBasePath(DEFAULT_BASE_PATH);
    }

    @Override
    protected Future<OpenAPIContract> getOpenAPIContract(Vertx vertx, JsonObject config) {
        return OpenAPIContract.from(vertx, CONTRACT_PATH.toString());
    }

    @Override
    protected Future<Router> createRouter(Vertx vertx, RouterBuilder routerBuilder) {
        routerBuilder.getRoute("createPets").addHandler(createResponseValidationHandler((req, rtx) -> {
            String name = req.getBody().getJsonObject().getString("name");
            int id = pets.size() + 1;
            JsonObject pet = new JsonObject().put("id", id).put("name", name);
            pets.put(pet.getInteger("id"), pet);

            return succeededFuture(ValidatableResponse.create(201));
        }));

        routerBuilder.getRoute("listPets").addHandler(createResponseValidationHandler((req, rtx) -> {
            JsonArray response = new JsonArray();
            pets.values().forEach(response::add);

            ValidatableResponse resp =
                    ValidatableResponse.create(200, response.toBuffer(), APPLICATION_JSON.toString());
            return succeededFuture(resp);
        }));

        routerBuilder.getRoute("showPetById").addHandler(createResponseValidationHandler((req, rtx) -> {
            JsonObject pet = pets.get(req.getPathParameters().get("petId").getInteger());
            if (pet == null) {
                return succeededFuture(ValidatableResponse.create(404));
            } else {
                return succeededFuture(ValidatableResponse.create(200, pet.toBuffer(), APPLICATION_JSON.toString()));
            }
        }));

        return succeededFuture(routerBuilder.createRouter());
    }
}
