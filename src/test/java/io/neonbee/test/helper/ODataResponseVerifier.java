package io.neonbee.test.helper;

import static com.google.common.truth.Truth.assertThat;
import static io.vertx.core.Future.future;

import java.util.List;
import java.util.function.Consumer;

import com.google.common.collect.Range;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;

public interface ODataResponseVerifier {

    /**
     * Matches if the response has a status code 2xx or 3xx and is equal to the expected {@link String}. This is
     * especially useful when the response is only a String like the response for a {@code /$count} request.
     *
     * @param response     A {@link Future} with the OData response
     * @param expectedBody The expected body of the OData response
     * @param testContext  The Vert.x test context
     * @return A succeeding Future if the expected body matches the response body, a failing Future otherwise.
     */
    default Future<Void> assertOData(Future<HttpResponse<Buffer>> response, String expectedBody,
            VertxTestContext testContext) {
        return assertOData(response, body -> assertThat(body.toString()).isEqualTo(expectedBody), testContext);
    }

    /**
     * Matches if the response has a status code 2xx or 3xx and the response body is equal to the expected
     * {@link JsonObject}.
     *
     * @param response     A {@link Future} with the OData response
     * @param expectedBody The expected body of the OData response
     * @param testContext  The Vert.x test context
     * @return A succeeding Future if the expected body matches the response body, a failing Future otherwise.
     */
    default Future<Void> assertOData(Future<HttpResponse<Buffer>> response, JsonObject expectedBody,
            VertxTestContext testContext) {
        return assertOData(response, body -> assertThat(body.toJsonObject()).isEqualTo(expectedBody), testContext);
    }

    /**
     * Matches if the response has a status code 2xx or 3xx and the logic of the passed assertion handler is valid for
     * the OData response.
     *
     * @param response      A {@link Future} with the OData response
     * @param assertHandler An assertion handler which contains the logic for the validation of the OData response
     * @param testContext   The Vert.x test context
     * @return A succeeding Future if the logic of the assertHandler matches the response, a failing Future otherwise.
     */
    default Future<Void> assertOData(Future<HttpResponse<Buffer>> response, Consumer<Buffer> assertHandler,
            VertxTestContext testContext) {
        return future(promise -> response.onComplete(testContext.succeeding(r -> {
            testContext.verify(() -> {
                assertThat(r.statusCode()).isIn(Range.closed(200, 399));
                assertHandler.accept(r.bodyAsBuffer());
            });
            promise.complete();
        })));
    }

    /**
     * Matches if the response has a status code 2xx or 3xx and the logic of the passed assertion handler is valid for a
     * requested entity property of the OData response.
     *
     * @param response      A {@link Future} with the OData response of an entity
     * @param assertHandler An assertion handler which contains the logic for the validation of the OData response of a
     *                      entity property.
     * @param testContext   The Vert.x test context
     * @return A succeeding Future if the logic of the assertHandler matches the response, a failing Future otherwise.
     */
    default Future<Void> assertODataProperty(Future<HttpResponse<Buffer>> response, Consumer<Object> assertHandler,
            VertxTestContext testContext) {
        return assertOData(response, body -> assertHandler.accept(body.toJsonObject().getValue("value")), testContext);
    }

    /**
     * Matches if the response has a status code 2xx or 3xx and if the properties of the entity of the OData response
     * match with a certain {@link JsonObject entity}. {@code @odata} annotations are not considered in the comparison.
     *
     * @param response       A {@link Future} with the OData response of an entity
     * @param expectedEntity The expected value of the OData response
     * @param testContext    The Vert.x test context
     * @return A succeeding Future if the expected value matches the response, a failing Future otherwise.
     */
    default Future<Void> assertODataEntity(Future<HttpResponse<Buffer>> response, JsonObject expectedEntity,
            VertxTestContext testContext) {
        return assertODataEntity(response, entity -> assertThat(entity).isEqualTo(expectedEntity), testContext);
    }

    /**
     * Matches if the response has a status code 2xx or 3xx and the logic of the passed assertion handler is valid for a
     * single requested entity of the OData response.
     *
     * @param response      A {@link Future} with the OData response of an entity
     * @param assertHandler An assertion handler which contains the logic for the validation of the OData response of a
     *                      single entity
     * @param testContext   The Vert.x test context
     * @return A succeeding Future if the logic of the assertHandler matches the response, a failing Future otherwise.
     */
    default Future<Void> assertODataEntity(Future<HttpResponse<Buffer>> response, Consumer<JsonObject> assertHandler,
            VertxTestContext testContext) {
        return assertOData(response, body -> assertHandler.accept(tidyMetaTags(body.toJsonObject())), testContext);
    }

    /**
     * Matches if the response has a status code 2xx or 3xx and a certain list of {@link JsonObject elements} is a
     * subset of the entities in the OData response.
     *
     * @param response         A {@link Future} with the OData response of an entity set
     * @param expectedEntities An expected list that should be included in the OData response
     * @param testContext      The Vert.x test context
     * @return A succeeding Future if the expected list with elements of type {@link JsonObject} is included as a subset
     *         in the response, a failing Future otherwise.
     */
    default Future<Void> assertODataEntitySetContains(Future<HttpResponse<Buffer>> response,
            List<JsonObject> expectedEntities, VertxTestContext testContext) {
        return assertODataEntitySet(response, value -> assertThat(value).containsAtLeastElementsIn(expectedEntities),
                testContext);
    }

    /**
     * Matches if the response has a status code 2xx or 3xx and if a certain list of {@link JsonObject elements} matches
     * exactly with the entities in the OData response.
     *
     * @param response         A {@link Future} with the OData response of an entity set
     * @param expectedEntities The expected value of the OData response
     * @param testContext      The Vert.x test context
     * @return A succeeding Future if the expected list exactly matches the response, a failing Future otherwise.
     */
    default Future<Void> assertODataEntitySetContainsExactly(Future<HttpResponse<Buffer>> response,
            List<JsonObject> expectedEntities, VertxTestContext testContext) {
        return assertODataEntitySet(response, value -> assertThat(value).containsExactlyElementsIn(expectedEntities),
                testContext);
    }

    /**
     * Matches if the response has a status code 2xx or 3xx and if the logic of the passed assertion handler is valid
     * for the entities of the OData response.
     *
     * @param response      A {@link Future} with the OData response of an entity set
     * @param assertHandler An assertion handler which contains the logic for the validation of the OData response of an
     *                      entity set
     * @param testContext   The Vert.x test context
     * @return A succeeding Future if the logic of the assertHandler matches the response, a failing Future otherwise.
     */
    default Future<Void> assertODataEntitySet(Future<HttpResponse<Buffer>> response, Consumer<JsonArray> assertHandler,
            VertxTestContext testContext) {
        return assertOData(response, value -> {
            JsonObject entities = value.toJsonObject();
            testContext.verify(() -> assertHandler.accept(
                    entities.containsKey("value") ? entities.getJsonArray("value") : new JsonArray().add(entities)));
        }, testContext);
    }

    private JsonObject tidyMetaTags(JsonObject body) {
        body.remove("@odata.context");
        body.remove("@odata.metadataEtag");
        return body;
    }
}
