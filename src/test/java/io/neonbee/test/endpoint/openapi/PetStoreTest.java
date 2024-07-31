package io.neonbee.test.endpoint.openapi;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.neonbee.config.EndpointConfig;
import io.neonbee.config.ServerConfig;
import io.neonbee.internal.verticle.ServerVerticle;
import io.neonbee.test.base.NeonBeeTestBase;
import io.neonbee.test.helper.WorkingDirectoryBuilder;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;

class PetStoreTest extends NeonBeeTestBase {

    private static final String PET1_NAME = "Peter";

    private static final String PET2_NAME = "Horst";

    private static final JsonObject PET1 = new JsonObject().put("id", 1).put("name", PET1_NAME);

    private static final JsonObject PET2 = new JsonObject().put("id", 2).put("name", PET2_NAME);

    @Override
    protected WorkingDirectoryBuilder provideWorkingDirectoryBuilder(TestInfo testInfo, VertxTestContext testContext) {
        WorkingDirectoryBuilder dirBuilder = WorkingDirectoryBuilder.standard();
        return dirBuilder.setCustomTask(workingDir -> {
            DeploymentOptions opts = WorkingDirectoryBuilder.readDeploymentOptions(ServerVerticle.class, workingDir);
            EndpointConfig epc = new EndpointConfig().setType(PetStoreEndpoint.class.getName());
            ServerConfig sc = new ServerConfig(opts.getConfig()).setEndpointConfigs(List.of(epc));
            opts.setConfig(sc.toJson());
            WorkingDirectoryBuilder.writeDeploymentOptions(ServerVerticle.class, opts, workingDir);
        });
    }

    @Test
    @DisplayName("should create, get and list pets")
    void cycleTest(VertxTestContext testContext) {
        Checkpoint create = testContext.checkpoint();
        Checkpoint get = testContext.checkpoint();
        Checkpoint list = testContext.checkpoint();

        createPet(new JsonObject().put("name", PET1_NAME)).compose(resp -> {
            testContext.verify(() -> assertThat(resp.statusCode()).isEqualTo(201));
            create.flag();
            return getPet("1");
        }).compose(pet -> {
            testContext.verify(() -> assertThat(pet).isEqualTo(PET1));
            get.flag();
            return createPet(new JsonObject().put("name", PET2_NAME)).compose(v -> getPets());
        }).onComplete(testContext.succeeding(pets -> {
            testContext.verify(() -> assertThat(pets).containsExactly(PET1, PET2));
            list.flag();
        }));
    }

    @Test
    @DisplayName("should fail when passed parameters are invalid")
    void validationTest(VertxTestContext testContext) {
        String expectedErrorMsg =
                "Error 400: The value of the request body is invalid. Reason: Instance does not have "
                        + "required property \"name\"";
        createPet(new JsonObject().put("invalidParam", PET1_NAME))
                .onComplete(testContext.succeeding(resp -> testContext.verify(() -> {
                    assertThat(resp.statusCode()).isEqualTo(400);
                    assertThat(resp.statusMessage()).isEqualTo("Bad Request");
                    assertThat(resp.bodyAsString()).contains(expectedErrorMsg);
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("should return 404 if endpoint doesn't exist")
    void testNotFound(VertxTestContext testContext) {
        super.createRequest(HttpMethod.GET, "/any404").send()
                .onComplete(testContext.succeeding(resp -> testContext.verify(() -> {
                    assertThat(resp.statusCode()).isEqualTo(404);
                    assertThat(resp.statusMessage()).isEqualTo("Not Found");
                    assertThat(resp.bodyAsString()).startsWith("Error 404: Not Found (Correlation ID:");
                    testContext.completeNow();
                })));
    }

    @Override
    public HttpRequest<Buffer> createRequest(HttpMethod method, String path) {
        return super.createRequest(method, PetStoreEndpoint.DEFAULT_BASE_PATH + path);
    }

    Future<HttpResponse<Buffer>> createPet(JsonObject pet) {
        return createRequest(HttpMethod.POST, "pets").sendJsonObject(pet);
    }

    Future<JsonArray> getPets() {
        return createRequest(HttpMethod.GET, "pets").send().map(HttpResponse::bodyAsJsonArray);
    }

    Future<JsonObject> getPet(String id) {
        return createRequest(HttpMethod.GET, "pets/" + id).send().map(HttpResponse::bodyAsJsonObject);
    }
}
